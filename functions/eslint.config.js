const tseslint = require("typescript-eslint");
const js = require("@eslint/js");
const globals = require("globals");

module.exports = tseslint.config(
  {
    ignores: [
      "lib/**/*",
      "generated/**/*",
      "scripts/**/*",
      "src/**/*.spec.ts",
      "*.js",
      ".eslintrc.js",
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["src/**/*.ts"],
    languageOptions: {
      globals: {
        ...globals.node,
        ...globals.es2021,
      },
      parserOptions: {
        project: ["tsconfig.json"],
        tsconfigRootDir: __dirname,
      },
    },
    rules: {
      "quotes": ["error", "double"],
      "import/no-unresolved": 0,
      "indent": ["error", 2],
    },
  }
);
