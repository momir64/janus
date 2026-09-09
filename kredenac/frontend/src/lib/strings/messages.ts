// A "\n" marks where a message breaks when it is too wide for one line

export const LOGIN_MESSAGES = {
  browserBlocked: "Something went wrong. Your device\nor browser blocked the sign-in.",
  passkeyRejected: "Passkey verification failed.\nSelected passkey is invalid.",
  passkeyRetry: "Passkey verification failed.\nPlease try again.",
  passkeyCloned: "Possible passkey cloning detected.\nThis passkey has been disabled.",
  noConnection: "Connection failed. Couldn't\nreach the server.",
  tooManyLogins: "Too many login attempts.\nTry again later.",
  serverError: "Something went wrong\non our end.",
  emailInvalid: "Entered email\naddress is invalid.",
  emailMissing: "Please enter your\nemail address.",
  emailNotSent: "We couldn't send the email.\nTry again later.",
  tooManyEmails: "Too many verification requests.\nTry again later.",
  recoverySent: "A recovery email will be sent\nif the account exists.",
  registrationSent: "Check your inbox for\nregistration link.",
} as const;

export const VERIFY_MESSAGES = {
  registrationCancelled: "Passkey registration\nwas cancelled.",
  passkeyExists: "A passkey for that account already\nexists on this device.",
  tooManyAttempts: "Too many registration attempts.\nTry again later.",
  deviceCannotStore: "Your device can't store a passkey\nsecurely. Try a different device.",
  deviceUnsupported: "Your device doesn't\nsupport passkeys.",
  invalidDomain: "The website is being served\nfrom an invalid domain.",
  browserBlocked: "Something on this device or browser\nblocked the passkey setup.",
  registrationFailed: "Something went wrong while\nregistering your passkey.",
} as const;

export const FILE_MESSAGES = {
  fileTooLarge: "Selected file is too large.\nMaximum file size is 50MB.",
  filenameTooLong: "Selected file's name is too long.\nMaximum length is 255 characters.",
  uploadFailed: "Upload failed. Try to\nupload the file again.",
  fileLimitReached: "You have reached the file limit.\nMaximum is 100 files.",
  downloadFailed: "There was an error with\ndownloading {filename}.",
  fileMissing: "File {filename}\nno longer exists.",
  deleteFailed: "Failed to delete\n{filename}.",
  listFailed: "Failed to load\nthe files.",
} as const;

export const NOTE_MESSAGES = {
  noteEmpty: "A note can't be\ncompletely empty.",
  titleTooLong: "The title is too long.\nTitle limit is 200 characters.",
  bodyTooLong: "The body is too long.\nBody limit is 2000 characters.",
  saveFailed: "Failed to save\nthe note.",
  noteLimitReached: "You have reached the note limit.\nMaximum is 1000 notes.",
  noteMissing: "Requested note\nno longer exists.",
  deleteFailed: "Failed to delete\nthe note.",
  listFailed: "Failed to load\nthe notes.",
} as const;

export const SETTINGS_MESSAGES = {
  lastPasskeyRemoved: "You removed your last passkey.\nUse email recovery flow to log in again.",
  sessionPasskeyRemoved: "You removed your current session passkey.\nUse another passkey to log in again.",
  sessionPasskeyRemovedNarrow: "You removed your current session\npasskey. Use another passkey\nto log in again.",
  passkeyDeleteFailed: "Failed to delete the passkey.",
  accountDeleteFailed: "Failed to delete the account.",
  listFailed: "Failed to list your passkeys.",
  passkeyVerifyFailed: "Couldn't verify that passkey.\nPlease try again.",
  passkeyAddFailed: "Failed to add the new passkey.",
} as const;

export const GLOBAL_MESSAGES = {
  signedOut: "You have been signed out.\nPlease sign in again.",
} as const;

export type LoginMessage = keyof typeof LOGIN_MESSAGES;
export type NoteMessage = keyof typeof NOTE_MESSAGES;
