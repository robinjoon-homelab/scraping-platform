const FALLBACK_MESSAGE = "요청을 처리하지 못했습니다.";

export class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.name = "ApiError";
        this.status = status;
    }
}

// The meta tags exist only on pages served through the security chain.
export function csrfHeaders(root = document) {
    const token = root.querySelector('meta[name="_csrf"]')?.content;
    const header = root.querySelector('meta[name="_csrf_header"]')?.content;
    return token && header ? { [header]: token } : {};
}

// Non-JSON bodies may contain server internals, so they are never shown to the user.
export async function problemMessage(response) {
    try {
        const problem = await response.json();
        return problem?.detail || problem?.title || FALLBACK_MESSAGE;
    } catch {
        return FALLBACK_MESSAGE;
    }
}

// fetch follows the login redirect of an expired session, so a 200 can still be an HTML page.
async function readJsonBody(response) {
    if (response.redirected) throw new ApiError(FALLBACK_MESSAGE, response.status);
    const text = await response.text();
    if (text === "") return null;
    try {
        return JSON.parse(text);
    } catch {
        throw new ApiError(FALLBACK_MESSAGE, response.status);
    }
}

export async function requestJson(url, options = {}, { root = document, fetchImpl = fetch } = {}) {
    const { body, headers, ...rest } = options;
    const init = {
        ...rest,
        headers: {
            Accept: "application/json",
            ...(body === undefined ? {} : { "Content-Type": "application/json" }),
            ...csrfHeaders(root),
            ...headers,
        },
    };
    if (body !== undefined) init.body = JSON.stringify(body);

    const response = await fetchImpl(url, init);
    if (!response.ok) throw new ApiError(await problemMessage(response), response.status);
    return readJsonBody(response);
}
