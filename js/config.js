export const API_BASE_NATIVE = "";

export const isNative = window.Capacitor?.isNativePlatform?.() === true;

export const API_BASE = isNative ? API_BASE_NATIVE : "";
