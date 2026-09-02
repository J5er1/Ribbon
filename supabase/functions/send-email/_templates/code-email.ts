// The sign-in code email (§6.10), rendered by the hook.
//
// This is the same design as supabase/email-templates/otp.html — dark-first
// per the brand brief (§1, §8), one hero element, no confirmation link
// because there is no web sign-in to land on. The difference is only who
// fills in the blanks: that file is Go-template syntax for the Supabase
// dashboard to render, this is the same markup with the values passed in.
//
// ⚠️  They are two copies of one design, and only one of them is live: with
// the send_email hook enabled, GoTrue never renders the dashboard template.
// Keep this file as the source and treat the HTML one as the record of what
// the mail looks like if the hook is ever switched off — or fold them
// together when both have landed.

export interface CodeEmail {
  subject: string;
  html: string;
  text: string;
}

/** Escapes text bound for HTML. The code is digits-only by the time it
 *  reaches here, but the address is whatever GoTrue was handed. */
function escapeHTML(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function codeEmail(params: {
  code: string;
  recipient: string;
  minutesValid: number;
}): CodeEmail {
  const code = escapeHTML(params.code);
  const recipient = escapeHTML(params.recipient);
  const minutes = params.minutesValid;
  // The template this replaces said "use it soon" because it could not read
  // the expiry. The hook can, so it says the number (config.toml pins
  // auth.email.otp_expiry).
  const expiry = minutes === 1 ? "one minute" : `${minutes} minutes`;

  const html = `<!doctype html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="dark light">
<meta name="supported-color-schemes" content="dark light">
<title>Your Ribbon code</title>
<!--[if mso]>
<noscript><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml></noscript>
<![endif]-->
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:ital,opsz,wght@0,7..72,400;0,7..72,500&family=Alegreya+Sans:wght@400;500&family=Alegreya+Sans+SC:wght@400;500&display=swap">
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  table, td { mso-table-lspace: 0pt; mso-table-rspace: 0pt; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  body { margin: 0; padding: 0; width: 100% !important; background-color: #0A0806; }

  .rb-bg   { background-color: #0A0806; }
  .rb-card { background-color: #120F0B; border-color: #292118 !important; }
  .rb-raised { background-color: #1B1710; border-color: #292118 !important; }
  .rb-text { color: #F1E8D9 !important; }
  .rb-muted { color: #8E8271 !important; }
  .rb-rule { border-color: #292118 !important; }
  .rb-code { color: #E9A63F !important; }
  .rb-link { color: #8E8271 !important; }

  @media (prefers-color-scheme: light) {
    .rb-bg   { background-color: #F0ECE2 !important; }
    .rb-card { background-color: #FAF7F0 !important; border-color: #D5CCBA !important; }
    .rb-raised { background-color: #E6DFD1 !important; border-color: #D5CCBA !important; }
    .rb-text { color: #1E1913 !important; }
    .rb-muted { color: #6B6051 !important; }
    .rb-rule { border-color: #D5CCBA !important; }
    .rb-code { color: #A96218 !important; }
    .rb-link { color: #6B6051 !important; }
  }

  @media screen and (max-width: 600px) {
    .rb-container { width: 100% !important; }
    .rb-pad { padding-left: 20px !important; padding-right: 20px !important; }
    .rb-code-text { font-size: 32px !important; letter-spacing: 8px !important; }
  }
</style>
</head>
<body class="rb-bg" style="margin:0; padding:0; background-color:#0A0806;">

<!-- preheader, hidden — the inbox preview line -->
<div style="display:none; max-height:0; overflow:hidden; opacity:0; mso-hide:all;">
  A code is on its way. Use it soon, and only once.
  &#8203;&#847; &#8203;&#847; &#8203;&#847; &#8203;&#847; &#8203;&#847; &#8203;&#847; &#8203;&#847; &#8203;&#847;
</div>

<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="rb-bg" style="background-color:#0A0806;">
<tr>
<td align="center" style="padding: 40px 16px;">

  <table role="presentation" width="520" cellpadding="0" cellspacing="0" border="0" class="rb-container" style="width:520px; max-width:520px;">

    <!-- wordmark, set live rather than as an image so it survives both grounds -->
    <tr>
      <td align="center" style="padding-bottom: 28px;">
        <span class="rb-text" style="font-family: Literata, Georgia, 'Times New Roman', serif; font-weight: 400; font-size: 26px; letter-spacing: -0.01em; color:#F1E8D9;">Ribbon<span class="rb-code" style="color:#E9A63F;">.</span></span>
      </td>
    </tr>

    <!-- card -->
    <tr>
      <td class="rb-card" style="background-color:#120F0B; border:1px solid #292118; border-radius:18px;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
          <tr>
            <td class="rb-pad" align="center" style="padding: 40px 40px 36px;">

              <div class="rb-muted" style="font-family: 'Alegreya Sans SC', 'Alegreya Sans', Arial, sans-serif; font-size: 12px; letter-spacing: 0.16em; text-transform: uppercase; color:#8E8271; margin: 0 0 14px;">
                The code
              </div>

              <div class="rb-text" style="font-family: Literata, Georgia, 'Times New Roman', serif; font-weight: 400; font-size: 21px; line-height: 1.4; color:#F1E8D9; margin: 0 0 28px;">
                Type this in to sign in to Ribbon.
              </div>

              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="rb-raised" style="background-color:#1B1710; border:1px solid #292118; border-radius:12px;">
                <tr>
                  <td align="center" style="padding: 22px 12px;">
                    <span class="rb-code rb-code-text" style="font-family: Literata, Georgia, 'Times New Roman', serif; font-weight: 500; font-size: 40px; letter-spacing: 14px; color:#E9A63F;">
                      ${code}
                    </span>
                  </td>
                </tr>
              </table>

              <div class="rb-muted" style="font-family: 'Alegreya Sans', Arial, sans-serif; font-size: 13px; color:#8E8271; margin: 16px 0 0;">
                One-time code — good for ${expiry}.
              </div>

              <div class="rb-rule" style="border-top: 1px solid #292118; margin: 32px 0 24px;"></div>

              <div class="rb-muted" style="font-family: 'Alegreya Sans', Arial, sans-serif; font-size: 14px; line-height: 1.6; color:#8E8271; margin: 0;">
                If this wasn't you, leave this email alone — nothing else happens.
              </div>

            </td>
          </tr>
        </table>
      </td>
    </tr>

    <!-- footer -->
    <tr>
      <td align="center" style="padding: 28px 20px 0;">
        <div class="rb-muted" style="font-family: 'Alegreya Sans SC', 'Alegreya Sans', Arial, sans-serif; font-size: 11px; letter-spacing: 0.14em; text-transform: uppercase; color:#8E8271;">
          Ribbon &middot; Read it together
        </div>
        <div class="rb-link" style="font-family: 'Alegreya Sans', Arial, sans-serif; font-size: 12px; color:#8E8271; margin-top: 10px;">
          Sent to ${recipient}
        </div>
      </td>
    </tr>

  </table>

</td>
</tr>
</table>

</body>
</html>`;

  // Every HTML mail wants a plain-text twin: some clients show it, and its
  // absence is itself a spam signal.
  const text = [
    "Ribbon",
    "",
    "Type this in to sign in to Ribbon:",
    "",
    `    ${params.code}`,
    "",
    `One-time code — good for ${expiry}.`,
    "",
    "If this wasn't you, leave this email alone — nothing else happens.",
    "",
    "Ribbon · Read it together",
    `Sent to ${params.recipient}`,
  ].join("\n");

  return { subject: "Your Ribbon code", html, text };
}
