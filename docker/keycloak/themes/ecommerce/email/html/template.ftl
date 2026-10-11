<#macro emailLayout>
<!DOCTYPE html>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${realmName}</title>
</head>
<body style="margin: 0; padding: 32px 16px; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #0f172a; line-height: 1.5; -webkit-font-smoothing: antialiased;">
  <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 560px; margin: 0 auto; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05); overflow: hidden;">
    <!-- Brand Header -->
    <tr>
      <td style="padding: 24px 32px 20px; text-align: center; border-bottom: 1px solid #f1f5f9;">
        <span style="font-size: 22px; font-weight: 800; color: #2563eb; letter-spacing: -0.5px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
          DE013 <span style="color: #0f172a; font-weight: 700;">STORE</span>
        </span>
      </td>
    </tr>
    <!-- Main Content -->
    <tr>
      <td style="padding: 32px;">
        <#nested>
      </td>
    </tr>
    <!-- Footer -->
    <tr>
      <td style="padding: 24px 32px; background-color: #f8fafc; border-top: 1px solid #e2e8f0; text-align: center;">
        <p style="margin: 0 0 6px; font-size: 12px; color: #64748b;">
          Email n&#224;y &#273;&#432;&#7907;c g&#7917;i t&#7921; &#273;&#7897;ng t&#7915; h&#7878; th&#7889;ng <strong>DE013 STORE</strong>.
        </p>
        <p style="margin: 0; font-size: 12px; color: #94a3b8;">
          &#169; 2026 DE013 STORE. All rights reserved.
        </p>
      </td>
    </tr>
  </table>
</body>
</html>
</#macro>
