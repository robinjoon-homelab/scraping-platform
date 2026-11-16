import js from "@eslint/js";
import globals from "globals";

// These sinks turn strings into markup, which the strict CSP cannot make safe on its own.
const htmlSinks = [
    {
        selector: "AssignmentExpression[left.property.name=/^(innerHTML|outerHTML)$/]",
        message: "Use textContent or DOM nodes.",
    },
    { selector: "CallExpression[callee.property.name='insertAdjacentHTML']", message: "Use DOM nodes." },
    {
        selector: "CallExpression[callee.object.name='document'][callee.property.name=/^write(ln)?$/]",
        message: "Use DOM nodes.",
    },
];

export default [
    js.configs.recommended,
    {
        languageOptions: { ecmaVersion: "latest", sourceType: "module", globals: globals.browser },
        rules: {
            "no-eval": "error",
            "no-implied-eval": "error",
            "no-new-func": "error",
            "no-restricted-syntax": ["error", ...htmlSinks],
        },
    },
];
