using System.Text.Json;

namespace AlternAIs.Application;

/// <summary>Script que escribe un texto en la caja del chat y, si se pide, lo envía (mismo script que Android).</summary>
public static class AutoSendScript
{
    public static string Build(string text, bool send = true) => Template
        .Replace("__TEXT__", JsonSerializer.Serialize(text, Options))
        .Replace("__SEND__", send ? "true" : "false");

    private static readonly JsonSerializerOptions Options = new() { Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping };

    /// <summary>El script devuelve «ok» (con comillas) si encontró la caja.</summary>
    public static bool IsOk(string? scriptResult) => scriptResult?.Contains("ok") == true;

    private const string Template = """
(function(t,doSend){
  var c=[].slice.call(document.querySelectorAll('div[contenteditable="true"],textarea')).filter(function(x){return x.offsetParent!==null;});
  var el=c.length?c[c.length-1]:null;
  if(!el) return 'none';
  el.focus();
  if(el.isContentEditable){
    var s=window.getSelection();s.selectAllChildren(el);s.collapseToEnd();
    if(!document.execCommand('insertText',false,t)) return 'none';
  } else {
    var set=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;
    set.call(el,(el.value||'')+t);
    el.dispatchEvent(new Event('input',{bubbles:true}));
  }
  if(doSend) setTimeout(function(){
    var b=[].slice.call(document.querySelectorAll('button')).filter(function(x){
      var l=(x.getAttribute('aria-label')||'')+' '+(x.getAttribute('data-testid')||'');
      return /send|enviar|submit/i.test(l)&&!x.disabled&&x.offsetParent!==null;});
    if(b.length){b[b.length-1].click();return;}
    ['keydown','keypress','keyup'].forEach(function(n){
      el.dispatchEvent(new KeyboardEvent(n,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));});
  },600);
  return 'ok';
})(__TEXT__,__SEND__)
""";
}
