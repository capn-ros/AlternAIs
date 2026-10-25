'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  getState: () => ipcRenderer.invoke('state:get'),
  getVersion: () => ipcRenderer.invoke('app:version'),
  completeTutorial: () => ipcRenderer.invoke('tutorial:complete'),
  minimizeWindow: () => ipcRenderer.invoke('window:minimize'),
  toggleMaximizeWindow: () => ipcRenderer.invoke('window:toggle-maximize'),
  closeWindow: () => ipcRenderer.invoke('window:close'),
  add: (data) => ipcRenderer.invoke('account:add', data),
  update: (id, patch) => ipcRenderer.invoke('account:update', id, patch),
  select: (id) => ipcRenderer.invoke('account:select', id),
  setService: (svc) => ipcRenderer.invoke('service:set', svc),
  reload: (id) => ipcRenderer.invoke('account:reload', id),
  wipe: (id) => ipcRenderer.invoke('account:wipe', id),
  remove: (id) => ipcRenderer.invoke('account:remove', id),
  setLimit: (id, until) => ipcRenderer.invoke('account:limit', id, until),
  menu: (id) => ipcRenderer.invoke('account:menu', id),
  modal: (open) => ipcRenderer.invoke('ui:modal', !!open),
  drawer: (open) => ipcRenderer.invoke('ui:drawer', !!open),
  setSettings: (patch) => ipcRenderer.invoke('settings:set', patch),
  exportAccounts: () => ipcRenderer.invoke('data:export'),
  importAccounts: () => ipcRenderer.invoke('data:import'),
  openDownloads: () => ipcRenderer.invoke('app:downloads'),
  onState: (cb) => ipcRenderer.on('state', (_e, s) => cb(s)),
  onAction: (cb) => ipcRenderer.on('action', (_e, a) => cb(a)),
  onWindowState: (cb) => ipcRenderer.on('window-state', (_e, s) => cb(s)),
});
