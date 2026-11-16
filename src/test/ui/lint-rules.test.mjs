import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { ESLint } from "eslint";
import { HtmlValidate } from "html-validate";
import stylelint from "stylelint";

const stylelintConfig = fileURLToPath(new URL("../../../config/ui/stylelint.config.mjs", import.meta.url));
const eslintConfig = fileURLToPath(new URL("../../../config/ui/eslint.config.mjs", import.meta.url));
const htmlConfig = JSON.parse(readFileSync(new URL("../../../config/ui/htmlvalidate.json", import.meta.url), "utf8"));

const component = "/x/static/ui/components.css";
const tokens = "/x/static/ui/tokens.css";
const theme = "/x/static/app/theme.css";

const cssRules = async (code, codeFilename) => {
    const { results } = await stylelint.lint({ code, codeFilename, configFile: stylelintConfig });
    return results.flatMap((result) => result.warnings.map((warning) => warning.rule));
};

const jsRules = async (code) => {
    const eslint = new ESLint({ overrideConfigFile: eslintConfig, cwd: process.cwd() });
    const results = await eslint.lintText(code, { filePath: "src/main/resources/static/ui/x.js" });
    return results.flatMap((result) => result.messages.map((message) => message.ruleId));
};

const htmlRules = async (body) => {
    const markup = `<!DOCTYPE html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n<title>t</title>\n</head>\n<body>\n${body}\n</body>\n</html>\n`;
    const report = await new HtmlValidate(htmlConfig).validateString(markup);
    return report.results.flatMap((result) => result.messages.map((message) => message.ruleId));
};

const cssCases = [
    ["a { color: #fff; }", component, "color-no-hex"],
    ["a { color: red; }", component, "color-named"],
    ["a { color: rgb(0 0 0); }", component, "function-disallowed-list"],
    ["a { color: light-dark(#000, #fff); }", component, "function-disallowed-list"],
    ["a { font-family: Arial; }", component, "declaration-property-value-allowed-list"],
    ["a { color: var(--ui-text) !important; }", component, "declaration-no-important"],
    ['@import "x.css";', component, "at-rule-disallowed-list"],
    ['a { background: url("https://x.test/y.png"); }', component, "function-url-scheme-allowed-list"],
    ['a { background: url("//x.test/y.png"); }', component, "function-url-no-scheme-relative"],
    [".x { --ui-accent: #123456; }", theme, "selector-disallowed-list"],
    [":root { color: #000; }", theme, "property-allowed-list"],
];

for (const [code, file, rule] of cssCases) {
    test(`stylelint reports ${rule} for ${code}`, async () => {
        assert.ok((await cssRules(code, file)).includes(rule));
    });
}

const cssAllowed = [
    [".ui-x { color: var(--ui-text); }", component],
    [":root { --ui-text: light-dark(#000, #fff); }", tokens],
    [":root { color-scheme: dark; }", theme],
    [":root { --ui-accent: #123456; }", theme],
];

for (const [code, file] of cssAllowed) {
    test(`stylelint allows ${code} in ${file}`, async () => {
        assert.deepEqual(await cssRules(code, file), []);
    });
}

const jsCases = [
    ["export const f = (el, v) => { el.innerHTML = v; };", "no-restricted-syntax"],
    ["export const f = (el, v) => { el.outerHTML = v; };", "no-restricted-syntax"],
    ['export const f = (el, v) => el.insertAdjacentHTML("beforeend", v);', "no-restricted-syntax"],
    ["export const f = (v) => document.write(v);", "no-restricted-syntax"],
    ["export const f = (v) => eval(v);", "no-eval"],
    ["const unused = 1;", "no-unused-vars"],
];

for (const [code, rule] of jsCases) {
    test(`eslint reports ${rule} for ${code}`, async () => {
        assert.ok((await jsRules(code)).includes(rule));
    });
}

test("eslint allows textContent assignment", async () => {
    assert.deepEqual(await jsRules("export const f = (el, v) => { el.textContent = v; };"), []);
});

test("html-validate reports input-missing-label", async () => {
    assert.ok((await htmlRules('<input name="x">')).includes("input-missing-label"));
});

test("html-validate reports wcag/h37", async () => {
    assert.ok((await htmlRules('<img src="x.png">')).includes("wcag/h37"));
});

test("html-validate reports no-dup-id", async () => {
    assert.ok((await htmlRules('<div id="a"></div><div id="a"></div>')).includes("no-dup-id"));
});

test("html-validate allows a labelled input", async () => {
    assert.deepEqual(await htmlRules('<label>Name <input type="text" name="x"></label>'), []);
});
