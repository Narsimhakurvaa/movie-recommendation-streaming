/// <reference types="vite/client" />

/** Typed access to the environment variables this app reads. */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_API_PROXY_TARGET?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

// Vite resolves CSS side-effect imports at build time; TypeScript needs telling.
declare module '*.css' {
  const content: string;
  export default content;
}
