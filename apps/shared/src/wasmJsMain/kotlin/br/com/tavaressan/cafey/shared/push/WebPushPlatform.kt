@file:OptIn(ExperimentalWasmJsInterop::class)

package br.com.tavaressan.cafey.shared.push

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

@JsFun("() => ('serviceWorker' in navigator) && ('PushManager' in window) && ('Notification' in window)")
external fun webPushIsSupported(): Boolean

@JsFun("() => (typeof Notification !== 'undefined') ? Notification.permission : 'denied'")
external fun webPushPermission(): String

@JsFun("() => navigator.serviceWorker.register('/sw.js').then(() => 'ok').catch(e => 'err:' + (e && e.message ? e.message : e))")
external fun webPushRegisterSw(): Promise<JsString>

@JsFun("() => Notification.requestPermission()")
external fun webPushRequestPermission(): Promise<JsString>

@JsFun(
    """
    (vapidPublicKey) => {
      function urlBase64ToUint8Array(base64String) {
        const padding = '='.repeat((4 - base64String.length % 4) % 4);
        const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
        const rawData = atob(base64);
        const outputArray = new Uint8Array(rawData.length);
        for (let i = 0; i < rawData.length; ++i) outputArray[i] = rawData.charCodeAt(i);
        return outputArray;
      }
      return navigator.serviceWorker.ready.then(reg => {
        return reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(vapidPublicKey)
        }).then(sub => {
          const json = sub.toJSON();
          const keys = json.keys || {};
          return JSON.stringify({
            endpoint: json.endpoint || sub.endpoint || '',
            p256dh: keys.p256dh || '',
            auth: keys.auth || ''
          });
        });
      });
    }
    """
)
external fun webPushSubscribe(vapidPublicKey: String): Promise<JsString>

@JsFun(
    """
    () => navigator.serviceWorker.ready.then(reg => reg.pushManager.getSubscription()).then(sub => {
      if (!sub) return '';
      const json = sub.toJSON();
      const keys = json.keys || {};
      return JSON.stringify({
        endpoint: json.endpoint || sub.endpoint || '',
        p256dh: keys.p256dh || '',
        auth: keys.auth || ''
      });
    }).catch(() => '')
    """
)
external fun webPushGetSubscriptionJson(): Promise<JsString>

@JsFun(
    """
    () => navigator.serviceWorker.ready.then(reg => reg.pushManager.getSubscription()).then(sub => {
      if (!sub) return '';
      const endpoint = sub.endpoint || '';
      return sub.unsubscribe().then(() => endpoint).catch(() => endpoint);
    }).catch(() => '')
    """
)
external fun webPushUnsubscribe(): Promise<JsString>

suspend fun <T : JsAny?> Promise<T>.awaitWasm(): T =
    kotlin.coroutines.suspendCoroutine { cont ->
        this.then(
            { value: T -> cont.resumeWith(Result.success(value)); null },
        ).catch { error: JsAny ->
            cont.resumeWith(Result.failure(Exception(error.toString())))
            null
        }
    }
