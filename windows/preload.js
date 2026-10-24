'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  getState: () => ipcRenderer.invoke('state:get'),
  add: (data) => ipcRenderer.invoke('account:add', data),
  update: (id, patch) => ipcRenderer.invoke('account:update', id, patch),
  select: (id) => ipcRenderer.invoke('account:select', id),
  reload: (id) => ipcRenderer.invoke('account:reload', id),
  wipe: (id) => ipcRenderer.invoke('account:wipe', id),
  remove: (id) => ipcRenderer.invoke('account:remove', id),
  setLimit: (id, until) => ipcRenderer.invoke('account:limit', id, until),
  menu: (id) => ipcRenderer.invoke('account:menu', id),
  modal: (open) => ipcRenderer.invoke('ui:modal', !!open),
  onState: (cb) => ipcRenderer.on('state', (_e, s) => cb(s)),
  onAction: (cb) => ipcRenderer.on('action', (_e, a) => cb(a)),
});
