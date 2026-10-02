export const API_BASE_NATIVE = "";

export const isNative = window.Capacitor?.isNativePlatform?.() === true;

export const API_BASE = isNative ? API_BASE_NATIVE : "";

export const TERMS_VERSION = "2026-09-30";

export const EDIT_WINDOW_MS = 15 * 60 * 1000;

export const EDIT_MAX = 5000;

export const FORWARD_MAX = 5;
