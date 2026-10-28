package app.claudeck

import org.json.JSONObject

/** Script que escribe un texto en la caja del chat y lo envía (lo usan la app y el envío en segundo plano). */
internal object AutoSend {
    fun js(text: String): String = """
            (function(t){
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
              setTimeout(function(){
                var b=[].slice.call(document.querySelectorAll('button')).filter(function(x){
                  var l=(x.getAttribute('aria-label')||'')+' '+(x.getAttribute('data-testid')||'');
                  return /send|enviar|submit/i.test(l)&&!x.disabled&&x.offsetParent!==null;});
                if(b.length){b[b.length-1].click();return;}
                ['keydown','keypress','keyup'].forEach(function(n){
                  el.dispatchEvent(new KeyboardEvent(n,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));});
              },600);
              return 'ok';
            })(${JSONObject.quote(text)})
        """.trimIndent()
}
