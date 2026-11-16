import assert from "node:assert/strict";
import { test } from "node:test";
import { ApiError, csrfHeaders, problemMessage, requestJson } from "../../main/resources/static/ui/api.js";

const rootWith = (token, header) => ({
    querySelector: (selector) => {
        if (selector === 'meta[name="_csrf"]') return token === undefined ? null : { content: token };
        if (selector === 'meta[name="_csrf_header"]') return header === undefined ? null : { content: header };
        return null;
    },
});

const jsonResponse = (status, body) =>
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/problem+json" } });

test("csrfHeaders returns the header from the meta tags", () => {
    assert.deepEqual(csrfHeaders(rootWith("t", "X-CSRF-TOKEN")), { "X-CSRF-TOKEN": "t" });
});

test("csrfHeaders is empty outside a security chain", () => {
    assert.deepEqual(csrfHeaders(rootWith()), {});
});

test("problemMessage prefers detail", async () => {
    assert.equal(await problemMessage(jsonResponse(400, { detail: "d", title: "t" })), "d");
});

test("problemMessage falls back to title", async () => {
    assert.equal(await problemMessage(jsonResponse(400, { title: "t" })), "t");
});

test("problemMessage hides non-JSON bodies", async () => {
    const response = new Response("<html>boom</html>", { status: 500 });
    assert.equal(await problemMessage(response), "요청을 처리하지 못했습니다.");
});

test("requestJson sends JSON with the CSRF header", async () => {
    let received;
    const fetchImpl = async (url, init) => {
        received = { url, init };
        return jsonResponse(200, { ok: true });
    };
    const body = { name: "n" };

    const result = await requestJson(
        "/api/x",
        { method: "POST", body },
        { root: rootWith("t", "X-CSRF-TOKEN"), fetchImpl },
    );

    assert.deepEqual(result, { ok: true });
    assert.equal(received.url, "/api/x");
    assert.equal(received.init.method, "POST");
    assert.equal(received.init.headers["X-CSRF-TOKEN"], "t");
    assert.equal(received.init.headers.Accept, "application/json");
    assert.equal(received.init.headers["Content-Type"], "application/json");
    assert.equal(received.init.body, JSON.stringify(body));
});

test("requestJson returns null for 204", async () => {
    const fetchImpl = async () => new Response(null, { status: 204 });
    assert.equal(await requestJson("/api/x", { method: "DELETE" }, { root: rootWith(), fetchImpl }), null);
});

test("requestJson returns null for an empty body", async () => {
    const fetchImpl = async () => new Response("", { status: 201 });
    assert.equal(await requestJson("/api/x", { method: "POST" }, { root: rootWith(), fetchImpl }), null);
});

const htmlResponse = () =>
    new Response("<html>login</html>", { status: 200, headers: { "Content-Type": "text/html" } });
const malformedResponse = () => new Response("{", { status: 200, headers: { "Content-Type": "application/json" } });
const followedRedirect = () => Object.defineProperty(jsonResponse(200, { ok: true }), "redirected", { value: true });

for (const [name, respond] of [
    ["an HTML page", htmlResponse],
    ["malformed JSON", malformedResponse],
    ["a followed redirect", followedRedirect],
]) {
    test(`requestJson rejects ${name} with ApiError`, async () => {
        await assert.rejects(
            requestJson("/api/x", {}, { root: rootWith(), fetchImpl: async () => respond() }),
            (error) => {
                assert.ok(error instanceof ApiError);
                assert.equal(error.status, 200);
                assert.equal(error.message, "요청을 처리하지 못했습니다.");
                return true;
            },
        );
    });
}

test("requestJson rejects with ApiError", async () => {
    const fetchImpl = async () => jsonResponse(409, { detail: "conflict", title: "Conflict" });
    await assert.rejects(requestJson("/api/x", {}, { root: rootWith(), fetchImpl }), (error) => {
        assert.ok(error instanceof ApiError);
        assert.equal(error.status, 409);
        assert.equal(error.message, "conflict");
        return true;
    });
});
