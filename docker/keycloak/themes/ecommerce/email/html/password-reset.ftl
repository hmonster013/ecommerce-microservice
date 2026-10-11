<#import "template.ftl" as layout>
<@layout.emailLayout>
  <h2 style="margin: 0 0 16px; font-size: 20px; font-weight: 700; color: #0f172a; text-align: center;">
    ${msg("passwordResetTitle")}
  </h2>

  <p style="margin: 0 0 14px; font-size: 15px; line-height: 1.6; color: #334155;">
    ${msg("passwordResetGreeting")}
  </p>

  <p style="margin: 0 0 24px; font-size: 15px; line-height: 1.6; color: #334155;">
    ${msg("passwordResetInstructionPrefix")} <strong style="color: #0f172a;">DE013 STORE</strong>. ${msg("passwordResetInstructionSuffix")}
  </p>

  <!-- CTA Button -->
  <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="margin: 28px 0;">
    <tr>
      <td align="center">
        <a href="${link}" style="display: inline-block; padding: 14px 32px; font-size: 15px; font-weight: 700; color: #ffffff; background-color: #2563eb; text-decoration: none; border-radius: 8px; box-shadow: 0 4px 10px rgba(37, 99, 235, 0.25);">
          ${msg("passwordResetButton")}
        </a>
      </td>
    </tr>
  </table>

  <!-- Expiration & Security Notice -->
  <div style="margin: 24px 0 20px; padding: 14px 18px; background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px;">
    <p style="margin: 0 0 6px; font-size: 13px; line-height: 1.5; color: #475569;">
      &#9201;&#65039; ${msg("passwordResetExpiryPrefix")} <strong style="color: #0f172a;">${linkExpirationFormatter(linkExpiration)}</strong>.
    </p>
    <p style="margin: 0; font-size: 13px; line-height: 1.5; color: #64748b;">
      ${msg("passwordResetIgnore")}
    </p>
  </div>

  <!-- Fallback Link -->
  <p style="margin: 20px 0 0; font-size: 12px; line-height: 1.5; color: #94a3b8; word-break: break-all;">
    ${msg("passwordResetFallback")}<br>
    <a href="${link}" style="color: #2563eb; text-decoration: underline; font-size: 11px; word-break: break-all;">${link}</a>
  </p>
</@layout.emailLayout>
