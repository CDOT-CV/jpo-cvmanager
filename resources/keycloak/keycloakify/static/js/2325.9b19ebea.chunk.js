"use strict";(self.webpackChunkkeycloakify_starter=self.webpackChunkkeycloakify_starter||[]).push([[2325],{4880:function(e,s,c){c.r(s),c.d(s,{default:function(){return i}});var a=c(8258),t=c(6055),l=c(7747),r=c(7107),n=c.p+"static/media/cdot_logo.70ed66aa73d2e820acb6.png",o=c(184);function i(e){var s=e.displayInfo,c=void 0!==s&&s,i=e.displayMessage,d=void 0===i||i,p=e.displayRequiredFields,u=void 0!==p&&p,f=e.displayWide,m=void 0!==f&&f,k=e.showAnotherWayIfPresent,h=void 0===k||k,C=e.headerNode,v=e.showUsernameNode,g=void 0===v?null:v,x=e.infoNode,y=void 0===x?null:x,j=e.kcContext,b=e.i18n,w=e.doUseDefaultCss,N=e.classes,F=e.children,A=(0,r.v)({doUseDefaultCss:w,classes:N}).getClassName,L=b.msg,I=b.changeLocale,W=b.labelBySupportedLanguageTag,S=b.currentLanguageTag,T=j.realm,E=j.locale,H=j.auth,_=j.url,P=j.message,B=j.isAppInitiatedAction;return(0,l.M)({doFetchDefaultThemeResources:w,url:_,stylesCommon:["node_modules/patternfly/dist/css/patternfly.min.css","node_modules/patternfly/dist/css/patternfly-additions.min.css","lib/zocial/zocial.css"],styles:["css/login.css"],htmlClassName:A("kcHtmlClass"),bodyClassName:void 0}).isReady?(0,o.jsxs)("div",{className:A("kcLoginClass"),children:[(0,o.jsx)("div",{id:"kc-header",className:A("kcHeaderClass"),children:(0,o.jsxs)("div",{id:"kc-header-wrapper",className:A("kcHeaderWrapperClass"),children:[(0,o.jsx)("img",{id:"kc-logo",src:n,alt:""}),L("loginTitleHtml",T.displayNameHtml)]})}),(0,o.jsxs)("div",{className:(0,t.W)(A("kcFormCardClass"),m&&A("kcFormCardAccountClass")),children:[(0,o.jsxs)("header",{className:A("kcFormHeaderClass"),children:[T.internationalizationEnabled&&((0,a.h)(void 0!==E),!0)&&E.supported.length>1&&(0,o.jsx)("div",{id:"kc-locale",children:(0,o.jsx)("div",{id:"kc-locale-wrapper",className:A("kcLocaleWrapperClass"),children:(0,o.jsxs)("div",{className:"kc-dropdown",id:"kc-locale-dropdown",children:[(0,o.jsx)("a",{href:"#",id:"kc-current-locale-link",children:W[S]}),(0,o.jsx)("ul",{children:E.supported.map((function(e){var s=e.languageTag;return(0,o.jsx)("li",{className:"kc-dropdown-item",children:(0,o.jsx)("a",{href:"#",onClick:function(){return I(s)},children:W[s]})},s)}))})]})})}),void 0===H||!H.showUsername||H.showResetCredentials?u?(0,o.jsxs)("div",{className:A("kcContentWrapperClass"),children:[(0,o.jsx)("div",{className:(0,t.W)(A("kcLabelWrapperClass"),"subtitle"),children:(0,o.jsxs)("span",{className:"subtitle",children:[(0,o.jsx)("span",{className:"required",children:"*"}),L("requiredFields")]})}),(0,o.jsx)("div",{className:"col-md-10",children:(0,o.jsx)("h1",{id:"kc-page-title",children:C})})]}):(0,o.jsx)("h1",{id:"kc-page-title",children:C}):u?(0,o.jsxs)("div",{className:A("kcContentWrapperClass"),children:[(0,o.jsx)("div",{className:(0,t.W)(A("kcLabelWrapperClass"),"subtitle"),children:(0,o.jsxs)("span",{className:"subtitle",children:[(0,o.jsx)("span",{className:"required",children:"*"})," ",L("requiredFields")]})}),(0,o.jsxs)("div",{className:"col-md-10",children:[g,(0,o.jsx)("div",{className:A("kcFormGroupClass"),children:(0,o.jsxs)("div",{id:"kc-username",children:[(0,o.jsx)("label",{id:"kc-attempted-username",children:null===H||void 0===H?void 0:H.attemptedUsername}),(0,o.jsx)("a",{id:"reset-login",href:_.loginRestartFlowUrl,children:(0,o.jsxs)("div",{className:"kc-login-tooltip",children:[(0,o.jsx)("i",{className:A("kcResetFlowIcon")}),(0,o.jsx)("span",{className:"kc-tooltip-text",children:L("restartLoginTooltip")})]})})]})})]})]}):(0,o.jsxs)(o.Fragment,{children:[g,(0,o.jsx)("div",{className:A("kcFormGroupClass"),children:(0,o.jsxs)("div",{id:"kc-username",children:[(0,o.jsx)("label",{id:"kc-attempted-username",children:null===H||void 0===H?void 0:H.attemptedUsername}),(0,o.jsx)("a",{id:"reset-login",href:_.loginRestartFlowUrl,children:(0,o.jsxs)("div",{className:"kc-login-tooltip",children:[(0,o.jsx)("i",{className:A("kcResetFlowIcon")}),(0,o.jsx)("span",{className:"kc-tooltip-text",children:L("restartLoginTooltip")})]})})]})})]})]}),(0,o.jsx)("div",{id:"kc-content",children:(0,o.jsxs)("div",{id:"kc-content-wrapper",children:[d&&void 0!==P&&("warning"!==P.type||!B)&&(0,o.jsxs)("div",{className:(0,t.W)("alert","alert-".concat(P.type)),children:["success"===P.type&&(0,o.jsx)("span",{className:A("kcFeedbackSuccessIcon")}),"warning"===P.type&&(0,o.jsx)("span",{className:A("kcFeedbackWarningIcon")}),"error"===P.type&&(0,o.jsx)("span",{className:A("kcFeedbackErrorIcon")}),"info"===P.type&&(0,o.jsx)("span",{className:A("kcFeedbackInfoIcon")}),(0,o.jsx)("span",{className:"kc-feedback-text",dangerouslySetInnerHTML:{__html:P.summary}})]}),F,void 0!==H&&H.showTryAnotherWayLink&&h&&(0,o.jsx)("form",{id:"kc-select-try-another-way-form",action:_.loginAction,method:"post",className:(0,t.W)(m&&A("kcContentWrapperClass")),children:(0,o.jsx)("div",{className:(0,t.W)(m&&[A("kcFormSocialAccountContentClass"),A("kcFormSocialAccountClass")]),children:(0,o.jsxs)("div",{className:A("kcFormGroupClass"),children:[(0,o.jsx)("input",{type:"hidden",name:"tryAnotherWay",value:"on"}),(0,o.jsx)("a",{href:"#",id:"try-another-way",onClick:function(){return document.forms["kc-select-try-another-way-form"].submit(),!1},children:L("doTryAnotherWay")})]})})}),c&&(0,o.jsx)("div",{id:"kc-info",className:A("kcSignUpClass"),children:(0,o.jsx)("div",{id:"kc-info-wrapper",className:A("kcInfoAreaWrapperClass"),children:y})})]})})]})]}):null}},2889:function(e,s,c){c.d(s,{a:function(){return l}});var a=c(6055),t=c(969);function l(e){var s=e.defaultClasses;return{useGetClassName:function(e){var c=e.classes;return{getClassName:(0,t.O)((function(e){return(0,a.W)(e,s[e],null===c||void 0===c?void 0:c[e])}))}}}}},7747:function(e,s,c){c.d(s,{M:function(){return p}});var a=c(4165),t=c(2982),l=c(5861),r=c(885),n=c(2791),o=c(1630),i=c(6196),d=c(6055);function p(e){var s=e.doFetchDefaultThemeResources,c=e.stylesCommon,d=void 0===c?[]:c,p=e.styles,f=void 0===p?[]:p,m=e.url,k=e.scripts,h=void 0===k?[]:k,C=e.htmlClassName,v=e.bodyClassName,g=(0,n.useReducer)((function(){return!0}),!s),x=(0,r.Z)(g,2),y=x[0],j=x[1];return(0,n.useEffect)((function(){if(s){var e=!1,c=[];return(0,l.Z)((0,a.Z)().mark((function s(){var l;return(0,a.Z)().wrap((function(s){for(;;)switch(s.prev=s.next){case 0:return l=[],[].concat((0,t.Z)(d.map((function(e){return(0,i.pathJoin)(m.resourcesCommonPath,e)}))),(0,t.Z)(f.map((function(e){return(0,i.pathJoin)(m.resourcesPath,e)})))).reverse().forEach((function(e){var s=(0,o.t)({type:"css",position:"prepend",href:e}),a=s.prLoaded,t=s.remove;c.push(t),l.push(a)})),s.next=4,Promise.all(l);case 4:if(!e){s.next=6;break}return s.abrupt("return");case 6:j();case 7:case"end":return s.stop()}}),s)})))(),h.forEach((function(e){var s=(0,o.t)({type:"javascript",src:(0,i.pathJoin)(m.resourcesPath,e)}).remove;c.push(s)})),function(){e=!0,c.forEach((function(e){return e()}))}}}),[]),u({target:"html",className:C}),u({target:"body",className:v}),{isReady:y}}function u(e){var s=e.target,c=e.className;(0,n.useEffect)((function(){if(void 0!==c){var e=document.getElementsByTagName(s)[0].classList,a=(0,d.W)(c).split(" ");return e.add.apply(e,(0,t.Z)(a)),function(){e.remove.apply(e,(0,t.Z)(a))}}}),[c])}},7107:function(e,s,c){c.d(s,{v:function(){return a}});var a=(0,c(2889).a)({defaultClasses:{kcHtmlClass:"login-pf",kcLoginClass:"login-pf-page",kcContentWrapperClass:"row",kcHeaderClass:"login-pf-page-header",kcHeaderWrapperClass:void 0,kcFormCardClass:"card-pf",kcFormCardAccountClass:"login-pf-accounts",kcFormSocialAccountClass:"login-pf-social-section",kcFormSocialAccountContentClass:"col-xs-12 col-sm-6",kcFormHeaderClass:"login-pf-header",kcLocaleWrapperClass:void 0,kcFeedbackErrorIcon:"pficon pficon-error-circle-o",kcFeedbackWarningIcon:"pficon pficon-warning-triangle-o",kcFeedbackSuccessIcon:"pficon pficon-ok",kcFeedbackInfoIcon:"pficon pficon-info",kcResetFlowIcon:"pficon pficon-arrow fa-2x",kcFormGroupClass:"form-group",kcLabelWrapperClass:"col-xs-12 col-sm-12 col-md-12 col-lg-12",kcSignUpClass:"login-pf-signup",kcInfoAreaWrapperClass:void 0,kcLogoClass:"login-pf-brand",kcContainerClass:"container-fluid",kcContentClass:"col-sm-8 col-sm-offset-2 col-md-6 col-md-offset-3 col-lg-6 col-lg-offset-3",kcFeedbackAreaClass:"col-md-12",kcLocaleClass:"col-xs-12 col-sm-1",kcAlertIconClasserror:"pficon pficon-error-circle-o",kcFormAreaClass:"col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-lg-8 col-lg-offset-2",kcFormSocialAccountListClass:"login-pf-social list-unstyled login-pf-social-all",kcFormSocialAccountDoubleListClass:"login-pf-social-double-col",kcFormSocialAccountListLinkClass:"login-pf-social-link",kcWebAuthnKeyIcon:"pficon pficon-key",kcWebAuthnDefaultIcon:"pficon pficon-key",kcFormClass:"form-horizontal",kcFormGroupErrorClass:"has-error",kcLabelClass:"control-label",kcInputClass:"form-control",kcInputErrorMessageClass:"pf-c-form__helper-text pf-m-error required kc-feedback-text",kcInputWrapperClass:"col-xs-12 col-sm-12 col-md-12 col-lg-12",kcFormOptionsClass:"col-xs-12 col-sm-12 col-md-12 col-lg-12",kcFormButtonsClass:"col-xs-12 col-sm-12 col-md-12 col-lg-12",kcFormSettingClass:"login-pf-settings",kcTextareaClass:"form-control",kcInfoAreaClass:"col-xs-12 col-sm-4 col-md-4 col-lg-5 details",kcFormGroupHeader:"pf-c-form__group",kcButtonClass:"btn",kcButtonPrimaryClass:"btn-primary",kcButtonDefaultClass:"btn-default",kcButtonLargeClass:"btn-lg",kcButtonBlockClass:"btn-block",kcInputLargeClass:"input-lg",kcSrOnlyClass:"sr-only",kcSelectAuthListClass:"list-group list-view-pf",kcSelectAuthListItemClass:"list-group-item list-view-pf-stacked",kcSelectAuthListItemFillClass:"pf-l-split__item pf-m-fill",kcSelectAuthListItemIconPropertyClass:"fa-2x select-auth-box-icon-properties",kcSelectAuthListItemIconClass:"pf-l-split__item select-auth-box-icon",kcSelectAuthListItemTitle:"select-auth-box-paragraph",kcSelectAuthListItemInfoClass:"list-view-pf-main-info",kcSelectAuthListItemLeftClass:"list-view-pf-left",kcSelectAuthListItemBodyClass:"list-view-pf-body",kcSelectAuthListItemDescriptionClass:"list-view-pf-description",kcSelectAuthListItemHeadingClass:"list-group-item-heading",kcSelectAuthListItemHelpTextClass:"list-group-item-text",kcAuthenticatorDefaultClass:"fa list-view-pf-icon-lg",kcAuthenticatorPasswordClass:"fa fa-unlock list-view-pf-icon-lg",kcAuthenticatorOTPClass:"fa fa-mobile list-view-pf-icon-lg",kcAuthenticatorWebAuthnClass:"fa fa-key list-view-pf-icon-lg",kcAuthenticatorWebAuthnPasswordlessClass:"fa fa-key list-view-pf-icon-lg",kcSelectOTPListClass:"card-pf card-pf-view card-pf-view-select card-pf-view-single-select",kcSelectOTPListItemClass:"card-pf-body card-pf-top-element",kcAuthenticatorOtpCircleClass:"fa fa-mobile card-pf-icon-circle",kcSelectOTPItemHeadingClass:"card-pf-title text-center",kcFormOptionsWrapperClass:void 0}}).useGetClassName},4943:function(){HTMLElement.prototype.prepend||(HTMLElement.prototype.prepend=function(e){if("string"===typeof e)throw new Error("Error with HTMLElement.prototype.appendFirst polyfill");this.insertBefore(e,this.firstChild)})},8258:function(e,s,c){c.d(s,{h:function(){return a.h}});var a=c(9883)},6055:function(e,s,c){c.d(s,{W:function(){return r}});var a=c(2982),t=c(9883),l=c(9465),r=function e(){for(var s=arguments.length,c=0,r="";c<s;c++){var n=c<0||arguments.length<=c?void 0:arguments[c];if(null!=n){var o=void 0;switch(typeof n){case"boolean":break;case"object":if(Array.isArray(n))o=e.apply(void 0,(0,a.Z)(n));else for(var i in(0,t.h)(!(0,l.z)(n,!1)),o="",n)n[i]&&i&&(o&&(o+=" "),o+=i);break;default:o=n}o&&(r&&(r+=" "),r+=o)}}return r}},1630:function(e,s,c){c.d(s,{t:function(){return t}});c(4943);var a=c(3172);function t(e){var s=document.createElement(function(){switch(e.type){case"css":return"link";case"javascript":return"script"}}()),c=new a.Deferred;return s.addEventListener("load",(function(){return c.resolve()})),Object.assign(s,function(){switch(e.type){case"css":return{href:e.href,type:"text/css",rel:"stylesheet",media:"screen,print"};case"javascript":return{src:e.src,type:"text/javascript"}}}()),document.getElementsByTagName("head")[0][function(){switch(e.type){case"javascript":return"appendChild";case"css":return function(){switch(e.position){case"append":return"appendChild";case"prepend":return"prepend"}}()}}()](s),{prLoaded:c.pr,remove:function(){return s.remove()}}}},9465:function(e,s,c){function a(e,s){return s}c.d(s,{z:function(){return a}})}}]);
//# sourceMappingURL=2325.9b19ebea.chunk.js.map