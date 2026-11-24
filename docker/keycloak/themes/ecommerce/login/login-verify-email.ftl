<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        ${msg("emailVerifyTitle")}
    <#elseif section = "form">
        <div class="kc-verify-email-card">
            <div class="kc-verify-email-icon" aria-hidden="true">
                <svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#2563eb" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect width="20" height="16" x="2" y="4" rx="2"/>
                    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
                </svg>
            </div>

            <p class="kc-verify-instruction">
                ${msg("emailVerifyInstruction1")}
            </p>

            <#assign targetEmail = (verifyEmail!(user.email)!"")>
            <#if targetEmail?has_content>
                <div class="kc-verify-email-target">
                    <span>${targetEmail}</span>
                </div>
            </#if>

            <p class="kc-verify-instruction-detail">
                ${msg("emailVerifyInstruction2")}
            </p>

            <#if isAppInitiatedAction??>
                <form id="kc-verify-email-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                            <#if verifyEmail??>
                                <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("emailVerifyResend")}" />
                            <#else>
                                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("emailVerifySend")}" />
                            </#if>
                            <button class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" type="submit" name="cancel-aia" value="true" formnovalidate>${msg("doCancel")}</button>
                        </div>
                    </div>
                </form>
            </#if>
        </div>
    <#elseif section = "info">
        <#if !isAppInitiatedAction??>
            <div class="kc-verify-resend-row">
                <span>${msg("emailVerifyInstruction3")}</span>
                <a href="${url.loginAction}" class="kc-resend-link">${msg("doClickHereToReSend")}</a>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
