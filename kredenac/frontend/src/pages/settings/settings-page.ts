import { addPasskey, reauthenticate, verifyForNewPasskey, type NewPasskeyChallenge } from "../../lib/webauthn/webauthn";
import { setContentTab, type ContentTab } from "../../lib/state/tab-state";
import { attachScrollbar } from "../../components/scroll-list/scrollbar";
import { closeCutEdge } from "../../components/scroll-list/closing-edge";
import { messageHint } from "../../components/message-hint/message-hint";
import { confirmDialog } from "../../components/dialog/confirm-dialog";
import { mount, onResize, ref, template } from "../../lib/render/dom";
import { alertDialog } from "../../components/dialog/alert-dialog";
import { SETTINGS_MESSAGES } from "../../lib/strings/messages";
import { appNav, logout } from "../../components/nav/nav";
import { passkeyCard } from "./passkey-card/passkey-card";
import { button } from "../../components/button/button";
import { isDesktop } from "../../lib/render/breakpoint";
import { navigate } from "../../lib/state/router";
import { failure } from "../../lib/http/failure";
import markup from "./settings-page.html?raw";
import type { PasskeyDto } from "../../types";
import { api } from "../../lib/http/api";

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

  function renderPasskeys(passkeys: PasskeyDto[]): void {
    const ordered = [
      ...passkeys.filter((passkey) => passkey.currentSession),
      ...passkeys.filter((passkey) => !passkey.currentSession),
    ];

    mount(
      list,
      ...ordered.map((passkey) =>
        passkeyCard({
          passkey,
          onDelete: () => {
            const last = passkeyCount === 1;
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
    alertDialog(
      [
        "Before deleting your account, please",
        "use one of your registered passkeys",
        "to verify that it's really you.",
      ],
      async () => {
        let token: string;
        try {
          token = await reauthenticate();
        } catch (error) {
          if (failure(error).dom !== "NotAllowedError")
            message.show(SETTINGS_MESSAGES.passkeyVerifyFailed);
          return;
        }

        confirmDialog(
          [
            "This action will permanently and irreversibly",
            "delete all data associated with this account.",
            "Are you sure you want to proceed?",
          ],
          async () => {
            try {
              await api.auth.deleteAccount(token);
            } catch {
              message.show(SETTINGS_MESSAGES.accountDeleteFailed);
              return;
            }
            navigate("/");
          },
          { frame: "wide" }
        );
      },
      { dismissible: true, frame: "wide" }
    );
  }

  function addNewPasskey(): void {
    alertDialog(
      [
        "Before adding a new passkey, please",
        "use one of your already registered",
        "passkeys to verify that it's you.",
      ],
      async () => {
        addButton.disabled = true;
        let registration: NewPasskeyChallenge;
        try {
          registration = await verifyForNewPasskey();
        } catch (error) {
          if (failure(error).dom !== "NotAllowedError")
            message.show(SETTINGS_MESSAGES.passkeyVerifyFailed);
          return;
        } finally {
          addButton.disabled = false;
        }

        alertDialog(["Now you can register", "a new passkey."], async () => {
          addButton.disabled = true;
          try {
            await addPasskey(registration, "Kredenac account");
            await loadPasskeys();
          } catch (error) {
            if (failure(error).dom !== "NotAllowedError")
              message.show(SETTINGS_MESSAGES.passkeyAddFailed);
          } finally {
            addButton.disabled = false;
          }
        }, { dismissible: true, frame: "compact" });
      },
      { dismissible: true, frame: "wide" }
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

  const addButton = button({
    label: "ADD NEW PASSKEY",
    variant: "framed",
    block: true,
    onClick: () => addNewPasskey(),
  });

  ref(page, "danger").append(
    addButton,
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
