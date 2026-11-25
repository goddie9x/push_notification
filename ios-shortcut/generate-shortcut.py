#!/usr/bin/env python3
"""Generate ios-shortcut/NotifyPush.shortcut (binary plist, WFWorkflow).

Generic companion for the NotifyPush iOS app. It carries a notification
verbatim into the app — NO parsing, NO bank logic, NO amount/balance/direction.

Layout of the workflow:
  1. Comment (config reminder)
  2. Dictionary: fallback webhook config (url/headerName/headerValue)
     -> Set Variable "FallbackConfig"
  3. Get Dictionary Value "url" -> Set Variable "FallbackUrl"
  4. Get Variable "Shortcut Input" (automation Notification / manual text)
     -> Set Variable "RawText"
  5. OTP filter: Match Text generic OTP patterns -> If result has any value
     -> Show Notification "skipped" + Stop Shortcut
  6. Current Date -> Format Date ISO-8601 -> Set Variable "ReceivedAt"
  7. Text: source app name (one shortcut copy per source app)
     -> Set Variable "SourceApp"
  8. Text: build the notify-push/v1 JSON body (9 fields, raw text verbatim)
     -> Set Variable "JsonBody"
  9. Fallback branch: If FallbackUrl is not "NOT-CONFIGURED", POST JsonBody
     to that single webhook and stop. Shortcuts cannot read the app's
     destination list, so the fallback is one URL from this Dictionary only.
 10. Primary path: URL-encode JsonBody, open
     pushnotification://ingest?payload=<encoded JSON>, notify.

Best-effort hand-built .shortcut file: structurally valid plist (validated
with plistlib on generation) using real Shortcuts action identifiers, but
NOT tested on a real iPhone — no iPhone is available in this environment.
The authoritative build spec is ios-shortcut/BUILD-GUIDE.md +
ios-shortcut/actions.json; if the import fails or behaves oddly, build the
Shortcut by hand from the guide.

Usage: python3 generate-shortcut.py [--source-app NAME]
"""
import argparse
import plistlib
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "NotifyPush.shortcut"

FALLBACK_URL_PLACEHOLDER = "NOT-CONFIGURED"
API_KEY_PLACEHOLDER = "PASTE_YOUR_API_KEY_HERE"

# Generic OTP/security-code patterns only (ICU regex, no lookbehind).
# No app-specific or bank-specific patterns: anything matching here is
# never forwarded, whatever the source app is.
OTP_RE = (
    r"otp|verification code|security code|one-time passcode|"
    r"one time passcode|do not share|"
    r"mã otp|mã xác thực|mã xác nhận|mã bảo mật|không chia sẻ"
)


def token_var(name):
    """A magic-variable token referencing a named variable."""
    return {"Type": "Variable", "VariableName": name}


def token_string(text, attachments):
    """A WFTextTokenString value: text with \\ufffc placeholders + attachments.

    attachments: list of (location, token_dict). Ranges are "{loc, 1}".
    """
    ranges = {}
    for loc, tok in attachments:
        ranges["{%d, 1}" % loc] = tok
    return {
        "Value": {"string": text, "attachmentsByRange": ranges},
        "WFSerializationType": "WFTextTokenString",
    }


def action(identifier, params, uuid_str=None):
    return {
        "WFWorkflowActionIdentifier": identifier,
        "WFWorkflowActionParameters": params,
        "WFWorkflowActionUUID": uuid_str or str(uuid.uuid4()).upper(),
    }


def comment(text):
    return action("is.workflow.actions.comment", {"WFCommentActionText": text})


def get_variable(name):
    return action(
        "is.workflow.actions.getvariable",
        {"WFVariable": {"Value": token_var(name), "WFSerializationType": "WFTextTokenAttachment"}},
    )


def set_variable(name):
    # Takes the previous action's output as its input.
    return action("is.workflow.actions.setvariable", {"WFVariableName": name})


def match_text(pattern):
    return action(
        "is.workflow.actions.text.match",
        {"WFMatchTextPattern": pattern, "WFMatchTextCaseSensitive": False},
    )


def show_notification(title, body):
    return action(
        "is.workflow.actions.notification",
        {"WFNotificationActionTitle": title, "WFNotificationActionBody": body},
    )


def conditional(mode, condition=None, string=None, input_var=None):
    """WFControlFlowMode: 0=If, 1=Else If, 2=Else, 3=End If."""
    params = {"WFControlFlowMode": mode}
    if condition is not None:
        params["WFCondition"] = condition
    if string is not None:
        params["WFConditionalActionString"] = string
    if input_var is not None:
        params["WFInput"] = {
            "Value": token_var(input_var),
            "WFSerializationType": "WFTextTokenAttachment",
        }
    return action("is.workflow.actions.conditional", params)


def text_action(text_value):
    return action("is.workflow.actions.gettext", {"WFTextActionText": text_value})


def set_text_variable(name, text_value):
    """Text action followed by Set Variable — used for constant strings."""
    return [text_action(text_value), set_variable(name)]


def dict_field(key, value):
    """One WFDictionaryFieldValueItems entry with literal text key and value."""
    text_value = {"string": value, "WFSerializationType": "WFTextTokenString"}
    key_value = {"string": key, "WFSerializationType": "WFTextTokenString"}
    return {
        "WFKey": {"Value": key_value, "WFSerializationType": "WFDictionaryFieldValue"},
        "WFValue": {"Value": text_value, "WFSerializationType": "WFDictionaryFieldValue"},
    }


def dict_token_field(key_var_name, value_var_name):
    """Dictionary entry with a variable token key and a variable token value."""
    return {
        "WFKey": {
            "Value": token_var(key_var_name),
            "WFSerializationType": "WFTextTokenAttachment",
        },
        "WFValue": {
            "Value": token_var(value_var_name),
            "WFSerializationType": "WFTextTokenAttachment",
        },
    }


def dictionary_action(items):
    return action(
        "is.workflow.actions.dictionary",
        {"WFDictionaryFieldValueItems": items},
    )


def get_dictionary_value(key):
    # Operates on the dictionary passed in as input.
    return action("is.workflow.actions.getvalueforkey", {"WFDictionaryKey": key})


def main():
    parser = argparse.ArgumentParser(description="Generate NotifyPush.shortcut")
    parser.add_argument(
        "--source-app",
        default="CHANGE-ME",
        help="Display name of the source app this copy is built for "
        "(one automation per source app; edit the SourceApp Text action "
        "in the Shortcuts editor to change it later).",
    )
    args = parser.parse_args()

    # JSON body template. \\ufffc marks token slots (order: SourceApp,
    # RawText, ReceivedAt, RawText). The notification text goes in verbatim
    # as body and rawText; nothing is parsed out of it.
    json_template = (
        "{\n"
        '  "schema": "notify-push/v1",\n'
        '  "sourceApp": "\ufffc",\n'
        '  "bundleId": "",\n'
        '  "title": "",\n'
        '  "subtitle": "",\n'
        '  "body": "\ufffc",\n'
        '  "receivedAt": "\ufffc",\n'
        '  "rawText": "\ufffc",\n'
        '  "device": "ios"\n'
        "}"
    )
    slots = []
    pos = 0
    for var in ("SourceApp", "RawText", "ReceivedAt", "RawText"):
        idx = json_template.index("\ufffc", pos)
        slots.append((idx, token_var(var)))
        pos = idx + 1
    json_body = token_string(json_template, slots)

    # Ingest URL template: one token slot for the URL-encoded JSON body.
    url_template = "pushnotification://ingest?payload=\ufffc"
    url_value = token_string(url_template, [(len("pushnotification://ingest?payload="), token_var("EncodedPayload"))])

    actions = [
        comment(
            "NotifyPush — forwards a notification verbatim to the NotifyPush "
            "iOS app (URL scheme pushnotification://ingest). CONFIG: set the "
            "SourceApp Text action below to the source app's name, and "
            "optionally fill the fallback webhook Dictionary (url/headerName/"
            "headerValue) to POST directly instead of opening the app. "
            "One automation per source app (iOS 27+). See BUILD-GUIDE.md."
        ),
        # 1. Fallback webhook config lives in this Dictionary only.
        #    Shortcuts cannot read the destinations stored in the app.
        dictionary_action([
            dict_field("url", FALLBACK_URL_PLACEHOLDER),
            dict_field("headerName", "X-Api-Key"),
            dict_field("headerValue", API_KEY_PLACEHOLDER),
        ]),
        set_variable("FallbackConfig"),
        get_variable("FallbackConfig"),
        get_dictionary_value("url"),
        set_variable("FallbackUrl"),
        # 2. Input: the Notification variable (automation) or shared text (manual).
        get_variable("Shortcut Input"),
        set_variable("RawText"),
        # 3. OTP filter: never forward one-time / security codes.
        get_variable("RawText"),
        match_text(OTP_RE),
        set_variable("OtpMatch"),
        get_variable("OtpMatch"),
        conditional(0, condition="Has Any Value"),
        show_notification("NotifyPush", "OTP / security code — not forwarded."),
        action("is.workflow.actions.exit", {}),
        conditional(3),
        # 4. Receive time as ISO-8601; the app also accepts epoch millis.
        action("is.workflow.actions.date", {}),
        action(
            "is.workflow.actions.format.date",
            {"WFDateFormatStyle": "Custom", "WFDateFormat": "yyyy-MM-dd'T'HH:mm:ssZZZZZ"},
        ),
        set_variable("ReceivedAt"),
        # 5. Source app name for this copy of the shortcut.
        *set_text_variable("SourceApp", args.source_app),
        # 6. Build the generic JSON body.
        text_action(json_body),
        set_variable("JsonBody"),
        # 7. Fallback branch: a configured webhook URL means POST directly
        #    and stop; the app never sees this notification.
        get_variable("FallbackUrl"),
        conditional(0, condition="Is Not", string=FALLBACK_URL_PLACEHOLDER),
        get_variable("FallbackConfig"),
        get_dictionary_value("headerName"),
        set_variable("FallbackHeaderName"),
        get_variable("FallbackConfig"),
        get_dictionary_value("headerValue"),
        set_variable("FallbackHeaderValue"),
        action(
            "is.workflow.actions.downloadurl",
            {
                "WFURLActionURL": {
                    "Value": token_var("FallbackUrl"),
                    "WFSerializationType": "WFTextTokenAttachment",
                },
                "WFURLActionHTTPMethod": "POST",
                "WFURLActionHeaders": {
                    "Value": {
                        "WFDictionaryFieldValueItems": [
                            dict_field("Content-Type", "application/json"),
                            dict_token_field("FallbackHeaderName", "FallbackHeaderValue"),
                        ]
                    },
                    "WFSerializationType": "WFDictionaryFieldValue",
                },
                "WFURLActionRequestBodyType": "JSON",
                "WFURLActionRequestBody": {
                    "Value": token_var("JsonBody"),
                    "WFSerializationType": "WFTextTokenAttachment",
                },
                "WFURLActionShowHeaders": True,
            },
        ),
        show_notification("NotifyPush", "Sent directly to the fallback webhook."),
        action("is.workflow.actions.exit", {}),
        conditional(3),
        # 8. Primary path: hand the payload to the app via its URL scheme.
        get_variable("JsonBody"),
        action("is.workflow.actions.urlencode", {"WFEncodeMode": "Encode"}),
        set_variable("EncodedPayload"),
        text_action(url_value),
        set_variable("IngestUrl"),
        get_variable("IngestUrl"),
        action(
            "is.workflow.actions.openurl",
            {
                "WFInput": {
                    "Value": token_var("IngestUrl"),
                    "WFSerializationType": "WFTextTokenAttachment",
                }
            },
        ),
        show_notification("NotifyPush", "Handed to the NotifyPush app."),
    ]

    workflow = {
        "WFWorkflowActions": actions,
        "WFWorkflowClientVersion": "2600",
        "WFWorkflowMinimumClientVersion": 2600,
        "WFWorkflowTypes": ["ActionExtension", "WatchKit"],
        "WFWorkflowIcon": {
            "WFWorkflowIconStartColor": 4282601983,
            "WFWorkflowIconGlyphNumber": 59471,
        },
        "WFWorkflowInputContentItemClasses": [
            "WFAppContentItem",
            "WFTextContentItem",
            "WFURLContentItem",
        ],
        "WFWorkflowImportQuestions": [],
        "WFWorkflowName": "NotifyPush",
        "WFWorkflowHasOutputFallback": False,
        "WFWorkflowHasShortcutInputVariables": True,
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "wb") as f:
        plistlib.dump(workflow, f, fmt=plistlib.FMT_BINARY)

    # Validate: parse it back and sanity-check the action list.
    with open(OUT, "rb") as f:
        back = plistlib.load(f)
    names = [a["WFWorkflowActionIdentifier"] for a in back["WFWorkflowActions"]]
    print(f"Wrote {OUT} ({OUT.stat().st_size} bytes)")
    print(f"Actions: {len(names)}")
    for i, n in enumerate(names, 1):
        print(f"  {i:2d}. {n}")
    print("Workflow name:", back["WFWorkflowName"])
    print("Client version:", back["WFWorkflowClientVersion"])


if __name__ == "__main__":
    main()
