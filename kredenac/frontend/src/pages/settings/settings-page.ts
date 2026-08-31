import { mount, onResize, ref, template } from "../../lib/dom";
import { navigate } from "../../lib/router";
import { api } from "../../lib/api";
import { button } from "../../components/button/button";
import { appNav, logout } from "../../components/nav/nav";
import markup from "./settings-page.html?raw";
import { passkeyCard } from "./passkey-card/passkey-card";
import { confirmDialog } from "../../components/dialog/confirm-dialog";
import { alertDialog } from "../../components/dialog/alert-dialog";
import { messageHint } from "../../components/message-hint/message-hint";
import { isDesktop } from "../../lib/breakpoint";
import { SETTINGS_MESSAGES } from "../../lib/messages";
import { attachScrollbar } from "../../components/scroll-list/scrollbar";
import { closeCutEdge } from "../../components/scroll-list/closing-edge";
import { setContentTab, type ContentTab } from "../../lib/tab-state";
import type { Passkey } from "../../types";

const build = template(markup);

export async function settingsPage(): Promise<Node> {
  const message = messageHint({ className: "settings-page__message", fitPadding: 32 });

  let passkeyCount = 0;

  async function loadPasskeys(): Promise<void> {
    try {
      const passkeys = await api.auth.listCredentials();
      passkeyCount = passkeys.length;
      renderPasskeys(passkeys);
    } catch {
      message.show(SETTINGS_MESSAGES.listFailed);
    }
  }

  function renderPasskeys(passkeys: Passkey[]): void {
    mount(
      list,
      ...passkeys.map((passkey) =>
        passkeyCard({
          passkey,
          onDelete: () => {
            const last = passkeyCount === 1;
            // TODO: the backend does not say which credential the session is
            //  running on - listCredentials returns { id, algorithm } and the
            //  session's credentialId lives only in the refresh token. Have
            //  it flag the current one, and this reads it.
            const current = passkey.currentSession === true;
            const question = ["Are you sure you want to delete", "this passkey?"];
            const lastQuestion = [
              "This is your last passkey. Are you sure",
              "you want to delete this passkey?",
            ];
            const currentQuestion =
              "This passkey is used for the current session. Deleting it will " +
              "cause you to log out. Are you sure you want to delete this passkey?";

            confirmDialog(
              last ? lastQuestion : current ? currentQuestion : question,
              async () => {
                try {
                  await api.auth.deleteCredential(passkey.id);
                } catch {
                  message.show(SETTINGS_MESSAGES.passkeyDeleteFailed);
                  return;
                }
                if (last || current) {
                  const session = isDesktop()
                    ? SETTINGS_MESSAGES.sessionPasskeyRemoved
                    : SETTINGS_MESSAGES.sessionPasskeyRemovedNarrow;
                  const told = last ? SETTINGS_MESSAGES.lastPasskeyRemoved : session;
                  alertDialog(told.split("\n"), () => void logout(), {
                    frame: last ? undefined : "session",
                  });
                  return;
                }
                await loadPasskeys();
              },
              { frame: last ? "roomy" : current ? "session" : undefined }
            );
          },
        })
      )
    );
  }

  function deleteAccount(): void {
    confirmDialog(
      [
        "This action will permanently and irreversibly",
        "delete all data associated with this account.",
        "Are you sure you want to proceed?",
      ],
      async () => {
        try {
          await api.auth.deleteAccount();
        } catch {
          message.show(SETTINGS_MESSAGES.accountDeleteFailed);
          return;
        }
        navigate("/");
      },
      { frame: "wide" }
    );
  }

  const [top, bottom] = appNav({
    active: "settings",
    onTabChange: (tab) => {
      if (tab === "settings") return;
      setContentTab(tab as ContentTab);
      navigate("/");
    },
  });

  const page = build();
  const list = ref(page, "list");
  const layout = ref(page, "layout");
  const frame = ref(page, "frame");

  ref(page, "danger").append(
    button({ label: "DELETE ACCOUNT", variant: "framed", danger: true, onClick: deleteAccount })
  );

  frame.prepend(top);
  page.append(bottom);

  function placeMessage(): void {
    const host = isDesktop() ? top : page;
    if (message.el.parentElement === host) return;
    if (host === top) top.insertBefore(message.el, top.lastElementChild);
    else page.append(message.el);
  }

  onResize(page, placeMessage);
  placeMessage();

  attachScrollbar(list).classList.add("settings-page__cards");
  attachScrollbar(layout, { inset: 0 });

  closeCutEdge(list);
  closeCutEdge(layout, list);

  void loadPasskeys();
  return page;
}
