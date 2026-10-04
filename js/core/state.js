export const state = {
  users: new Map(),
  chats: [],
  active: null,
  unsubs: [],
  pendingName: "",
  blocked: new Set(),
  blockedReady: false,
  deleting: false,
  muted: {},
  callDocs: new Map(),
  callHistoryOn: false,
  chatsReady: false,
  lastMessageDocs: null,
  pendingChat: new URLSearchParams(location.search).get("chat"),
  usersLoaded: false,
  searchState: { term: "", status: "idle", user: null },
  filter: "all"
};
