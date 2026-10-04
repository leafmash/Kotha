import { addDoc, collection, doc, getDoc, onSnapshot, query, serverTimestamp, setDoc, updateDoc, where } from "../../core/sdk.js";
import { createCalls } from "../../call.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { auth, db, triggerPush } from "../../core/firebase.js";
import { pic } from "../../core/format.js";
import { send } from "../chat/send.js";
import { askChoice } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";

export let calls = null;

export function initCalls() {
  calls = createCalls({
    auth,
    db,
    push: triggerPush,
    fs: { collection, doc, getDoc, setDoc, updateDoc, addDoc, onSnapshot, query, where, serverTimestamp },
    $,
    toast,
    pic,
    getActive: () => state.active,
    getUsers: () => state.users,
    send,
    askChoice
  });
}
