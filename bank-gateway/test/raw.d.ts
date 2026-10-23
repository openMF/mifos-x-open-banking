// Vite `?raw` imports (test/docs.test.ts): the file's text, bundled at build time.
declare module "*?raw" {
  const text: string;
  export default text;
}
