export default {
    extends: ["stylelint-config-standard"],
    rules: {
        "color-no-hex": true,
        "color-named": "never",
        "function-disallowed-list": [
            "rgb",
            "rgba",
            "hsl",
            "hsla",
            "hwb",
            "lab",
            "lch",
            "oklab",
            "oklch",
            "color",
            "light-dark",
        ],
        "declaration-property-value-allowed-list": {
            "font-family": ["/^var\\(--ui-font-/", "inherit"],
            font: ["/var\\(--ui-font-/", "inherit"],
        },
        "declaration-no-important": true,
        "at-rule-disallowed-list": ["import"],
        "function-url-scheme-allowed-list": ["data"],
        "function-url-no-scheme-relative": true,
    },
    // Globs are resolved against this config's directory unless absolute, so they start with "/".
    overrides: [
        {
            files: ["/**/static/ui/tokens.css", "/**/static/app/theme.css"],
            rules: {
                "color-no-hex": null,
                "color-named": null,
                "function-disallowed-list": null,
                "declaration-property-value-allowed-list": null,
            },
        },
        {
            files: ["/**/static/app/theme.css"],
            rules: {
                "selector-disallowed-list": ["/^(?!:root$).*/"],
                "property-allowed-list": ["/^--/", "color-scheme"],
            },
        },
    ],
};
