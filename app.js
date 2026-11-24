'use strict';

(() => {
  const STORAGE_KEY = 'relay.sms.prototype.v1';
  const $ = (id) => document.getElementById(id);
  const escape = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const icon = (name, className = '') => `<svg class="${className}" aria-hidden="true"><use href="#i-${name}"/></svg>`;
  const uuid = () => globalThis.crypto?.randomUUID?.() || `id-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const initialTime = new Date();
  const at = (hour, minute, daysAgo = 0) => { const d = new Date(initialTime); d.setDate(d.getDate() - daysAgo); d.setHours(hour, minute, 0, 0); return d.getTime(); };
  const message = (text, direction, timestamp, line = 'personal') => ({id:uuid(),text,direction,timestamp,line,status:direction === 'out' ? 'delivered' : 'received'});
  const initialState = () => ({
    version:1,
    settings:{personal:'Personal',work:'Work',defaultLine:'personal',assistant:true},
    conversations:[
      {id:'maya',name:'Maya Patel',number:'+44 7700 900142',initials:'MP',color:'#e8d6c9',ink:'#96705a',line:'personal',unread:0,starred:true,archived:false,topic:'coffee',messages:[
        message('Hey! How has your morning been? ☀️','in',at(10,24)),
        message('Pretty good! Finally got through my to-do list. How about you?','out',at(10,26)),
        message('Same here, it’s been a busy one. I think we’ve earned a coffee break.','in',at(10,28)),
        message('Absolutely. The little place on the corner?','out',at(10,30)),
        message('Yes! Coffee at 3? I’ll grab us a table by the window ☕','in',at(10,32))]},
      {id:'alex',name:'Alex Morgan',number:'+44 7700 900215',initials:'AM',color:'#d9e2ee',ink:'#6b82a1',line:'work',unread:2,starred:false,archived:false,topic:'project',messages:[
        message('Hey Deepak, I’ve put the updated mockups in the shared folder.','in',at(9,44),'work'),
        message('Thanks, Alex. I’ll take a look this morning.','out',at(9,46),'work'),
        message('Great! Would love your thoughts on the new dashboard.','in',at(10,18),'work'),
        message('Can we go through the latest designs tomorrow at 10?','in',at(10,21),'work')]},
      {id:'mum',name:'Mum',number:'+44 7700 900331',initials:'M',color:'#e9dce8',ink:'#a17f9d',line:'personal',unread:1,starred:true,archived:false,topic:'family',messages:[
        message('Hope you’re having a lovely week!','in',at(8,30)),
        message('Thanks Mum! Looking forward to Sunday.','out',at(8,37)),
        message('Sunday lunch at ours? I’m making your favourite 😊','in',at(9,58))]},
      {id:'studio',name:'Studio North',number:'+44 7700 900407',initials:'SN',color:'#e8e4d1',ink:'#938961',line:'work',unread:1,starred:false,archived:false,topic:'project',messages:[
        message('Hi Deepak, just confirming we received your brief.','in',at(8,30),'work'),
        message('Perfect, thanks for confirming!','out',at(8,42),'work'),
        message('The first round of concepts will be ready on Friday.','in',at(9,12),'work')]},
      {id:'jamie',name:'Jamie Wilson',number:'+44 7700 900512',initials:'JW',color:'#d5e5dc',ink:'#779b84',line:'personal',unread:0,starred:false,archived:false,topic:'general',messages:[
        message('Made it home! Thanks for a great evening.','in',at(21,12,1)),
        message('Such a good catch-up. Let’s do it again soon!','out',at(21,20,1))]},
      {id:'delivery',name:'Parcel delivery',number:'+44 7700 900618',initials:'PD',color:'#eadfce',ink:'#ab8e67',line:'personal',unread:0,starred:false,archived:false,topic:'delivery',messages:[
        message('Your parcel is out for delivery. We’ll arrive between 14:00 and 16:00.','in',at(14,6,1)),
        message('Your parcel has been delivered. Have a lovely day!','in',at(15,24,1))]},
      {id:'sam',name:'Sam Taylor',number:'+44 7700 900723',initials:'ST',color:'#dce1ef',ink:'#838cab',line:'work',unread:0,starred:false,archived:false,topic:'general',messages:[
        message('The invoice is all sorted. Thanks for your help with this.','in',at(11,35,1),'work'),
        message('Happy to help, Sam. Have a great weekend!','out',at(11,40,1),'work')]},
      {id:'archive-sample',name:'Old appointment',number:'+44 7700 900841',initials:'OA',color:'#e4e7de',ink:'#89977d',line:'personal',unread:0,starred:false,archived:true,topic:'general',messages:[message('Your appointment is confirmed. See you on Tuesday.','in',at(10,30,3))]}
    ]
  });

  function validState(value) {
    return value?.version === 1 && value.settings && ['personal','work'].every(key => typeof value.settings[key] === 'string' && value.settings[key].trim()) && ['personal','work'].includes(value.settings.defaultLine) && typeof value.settings.assistant === 'boolean' && Array.isArray(value.conversations) && value.conversations.every(c => typeof c.id === 'string' && typeof c.name === 'string' && typeof c.number === 'string' && typeof c.initials === 'string' && ['personal','work'].includes(c.line) && Array.isArray(c.messages) && c.messages.every(m => typeof m.id === 'string' && typeof m.text === 'string' && ['in','out'].includes(m.direction) && ['personal','work'].includes(m.line) && Number.isFinite(m.timestamp)));
  }
  let state = initialState();
  let persistenceAvailable = true;
  try { const saved = JSON.parse(localStorage.getItem(STORAGE_KEY)); if (validState(saved)) state = saved; } catch { persistenceAvailable = false; }
  // A browser closed during a simulated send must not leave messages stuck indefinitely.
  state.conversations.forEach(c => c.messages.forEach(m => { if (m.status === 'sending' || m.status === 'sent') m.status = 'delivered'; }));
  let selectedId = state.conversations.find(c => !c.archived)?.id || null;
  let view = 'inbox';
  let lineFilter = 'all';
  let assistantMode = 'replies';
  let suggestionOffset = 0;
  let toastTimer;
  const drafts = new Map();
  const draftLines = new Map();
  const active = () => state.conversations.find(c => c.id === selectedId);
  const lineLabel = line => state.settings[line];
  const lastMessage = c => c.messages[c.messages.length - 1];
  const initials = name => name.trim().split(/\s+/).slice(0,2).map(part => part[0]).join('').toUpperCase();
  const time = stamp => new Date(stamp).toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit'});
  const dayLabel = stamp => {
    const date = new Date(stamp), today = new Date();
    const yesterday = new Date(today); yesterday.setDate(today.getDate() - 1);
    if (date.toDateString() === today.toDateString()) return 'Today';
    if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
    return date.toLocaleDateString('en-GB',{day:'numeric',month:'short'});
  };
  const listTime = stamp => dayLabel(stamp) === 'Today' ? time(stamp) : dayLabel(stamp);
  function persist() {
    try { localStorage.setItem(STORAGE_KEY,JSON.stringify(state)); }
    catch { if (persistenceAvailable) toast('Browser storage is unavailable. Changes will last for this session.'); persistenceAvailable = false; }
  }
  function toast(text) { $('toast').textContent = text; $('toast').classList.add('visible'); clearTimeout(toastTimer); toastTimer = setTimeout(() => $('toast').classList.remove('visible'),3500); }
  function lineBadge(line) { return `<span class="line-badge ${line === 'work' ? 'work' : ''}">${icon('sim')}${escape(lineLabel(line))}</span>`; }
  function avatar(c) { const colors = /^#[0-9a-f]{6}$/i; return `<span class="avatar" style="background:${colors.test(c.color) ? c.color : '#e4e7de'};color:${colors.test(c.ink) ? c.ink : '#607653'}">${escape(c.initials)}</span>`; }
  function visibleConversations() {
    const query = $('search').value.toLowerCase().trim();
    return state.conversations.filter(c => {
      const matchesView = view === 'archived' ? c.archived : !c.archived && (view !== 'unread' || c.unread > 0) && (view !== 'starred' || c.starred);
      return matchesView && (lineFilter === 'all' || c.line === lineFilter) && (!query || [c.name,c.number,...c.messages.map(m => m.text)].join(' ').toLowerCase().includes(query));
    }).sort((a,b) => (lastMessage(b)?.timestamp || Number.MAX_SAFE_INTEGER) - (lastMessage(a)?.timestamp || Number.MAX_SAFE_INTEGER));
  }
  function renderLabels() {
    document.querySelectorAll('[data-personal-label]').forEach(el => el.textContent = state.settings.personal);
    document.querySelectorAll('[data-work-label]').forEach(el => el.textContent = state.settings.work);
    document.querySelectorAll('[data-filter]').forEach(el => el.classList.toggle('active',el.dataset.filter === lineFilter));
    document.querySelectorAll('[data-line]').forEach(el => el.classList.toggle('selected',el.dataset.line === lineFilter));
    document.querySelectorAll('[data-view]').forEach(el => el.classList.toggle('active',el.dataset.view === view));
    $('listTitle').textContent = ({inbox:'All messages',unread:'Unread messages',starred:'Starred messages',archived:'Archived messages'})[view];
    $('listSubtitle').textContent = lineFilter === 'all' ? 'Two lines. One happy inbox.' : `${lineLabel(lineFilter)} conversations, together.`;
    $('unreadCount').textContent = state.conversations.filter(c => !c.archived && c.unread).length;
    $('inboxCount').textContent = state.conversations.filter(c => !c.archived).length;
    const options = ['personal','work'].map((line,i) => `<option value="${line}">${escape(lineLabel(line))} · SIM ${i+1}</option>`).join('');
    $('sendLine').innerHTML = options;
    $('sendLine').value = draftLines.get(selectedId) || active()?.line || state.settings.defaultLine;
    $('newLine').innerHTML = options;
    $('defaultLine').innerHTML = options;
    document.querySelector('.suggested-row').hidden = !state.settings.assistant;
    $('rewriteButton').hidden = !state.settings.assistant;
    $('assistantPanel').hidden = !state.settings.assistant;
    if (!state.settings.assistant) $('appShell').classList.remove('assistant-open');
    $('appShell').classList.toggle('assistant-disabled',!state.settings.assistant);
  }
  function renderList() {
    const conversations = visibleConversations();
    $('conversationCount').textContent = String(conversations.length).padStart(2,'0');
    $('conversationItems').innerHTML = conversations.length ? conversations.map(c => {
      const last = lastMessage(c);
      return `<button class="conversation-item ${c.id === selectedId ? 'selected' : ''} ${c.unread ? 'unread' : ''}" data-conversation="${escape(c.id)}" aria-pressed="${c.id === selectedId}" aria-label="${escape(c.name)}${c.unread ? `, ${c.unread} unread` : ''}">${avatar(c)}<span class="conversation-info"><span class="conversation-name-row"><span class="conversation-name">${escape(c.name)}</span><span class="conversation-time">${last ? escape(listTime(last.timestamp)) : 'New'}</span></span><span class="conversation-preview" style="display:block">${last?.direction === 'out' ? 'You: ' : ''}${escape(last?.text || 'Say hello. Start a conversation.')}</span><span class="conversation-meta">${lineBadge(c.line)}${c.unread ? `<span class="unread-badge">${c.unread}</span>` : c.starred ? icon('star','small-star') : ''}</span></span></button>`;
    }).join('') : `<div class="empty-state">${icon(view === 'starred' ? 'star' : 'inbox')}<br><strong>${$('search').value ? 'No matches just yet.' : view === 'unread' ? 'You’re all caught up.' : 'Nothing here yet.'}</strong><br>${$('search').value ? 'Try another name, number or message.' : view === 'starred' ? 'Star a conversation to keep it close.' : 'Your conversations will appear here.'}</div>`;
    document.querySelector('.list-footer').innerHTML = icon('check') + (view === 'unread' && conversations.length ? `${conversations.length} conversations to catch up on.` : 'A little space for every conversation.');
  }
  function renderMessages() {
    const c = active(); if (!c) return;
    let previousDay = '';
    $('messages').innerHTML = c.messages.length ? c.messages.map(m => {
      const day = dayLabel(m.timestamp);
      const divider = day !== previousDay ? `<div class="date-separator">${escape(day)}</div>` : ''; previousDay = day;
      const statusText = m.status === 'sending' ? 'Sending…' : m.status === 'sent' ? 'Sent' : 'Delivered';
      return `${divider}<div class="message-row ${m.direction === 'out' ? 'outgoing' : ''}"><div class="bubble">${escape(m.text)}</div><div class="message-meta"><span>${escape(time(m.timestamp))}</span><span>·</span><span class="message-line">${escape(lineLabel(m.line))}</span>${m.direction === 'out' ? `${icon(m.status === 'delivered' ? 'double' : 'check')}<span>${statusText}</span>` : ''}</div></div>`;
    }).join('') : `<div class="empty-state">${icon('message')}<br>A new conversation, a fresh start.<br>Write your first message below.</div>`;
    $('messages').scrollTop = $('messages').scrollHeight;
  }
  function renderThread() {
    const c = active();
    if (!c) {
      $('threadHeader').innerHTML = '<div class="thread-contact"><h2>Your conversations</h2></div>';
      $('threadLineBar').innerHTML = '';
      $('messages').innerHTML = `<div class="thread-empty">${icon('message')}<h2>Make a little connection.</h2><p>Choose a conversation or start a new one.</p><button class="primary" data-action="new">New message</button></div>`;
      $('composerArea').hidden = true; return;
    }
    $('composerArea').hidden = false;
    $('threadHeader').innerHTML = `<button class="icon-button mobile-back" id="backButton" aria-label="Back to conversations">${icon('back')}</button>${avatar(c)}<div class="thread-contact"><h2>${escape(c.name)}</h2><p>${escape(c.number)}</p></div><div class="thread-header-actions"><button class="icon-button ${c.starred ? 'is-starred' : ''}" id="starButton" aria-label="${c.starred ? 'Unstar' : 'Star'} conversation" aria-pressed="${!!c.starred}">${icon('star')}</button><button class="icon-button" id="archiveButton" aria-label="${c.archived ? 'Restore' : 'Archive'} conversation" title="${c.archived ? 'Restore' : 'Archive'} conversation">${icon(c.archived ? 'inbox' : 'archive')}</button><button class="icon-button" id="assistantToggle" aria-label="Open reply assistant" ${state.settings.assistant ? '' : 'hidden'}>${icon('spark')}</button></div>`;
    renderLineBar();
    $('messageInput').value = drafts.get(c.id) || '';
    updateComposer(); renderMessages();
  }
  function renderLineBar() {
    const c = active(); if (!c) return;
    const chosen = draftLines.get(c.id) || c.line;
    $('sendLine').value = chosen;
    $('sendLine').parentElement.classList.toggle('work',chosen === 'work');
    $('threadLineBar').innerHTML = `<span>${icon('sim')}Conversation on ${lineBadge(c.line)}</span><span>${icon('lock')}${c.archived ? 'Archived conversation' : 'Saved on this device'}</span>`;
  }
  const ideaSets = {
    coffee:[['Sounds perfect! See you at 3. Window seat is the best seat ☕','Count me in! Looking forward to a proper catch-up.','3 works for me! Shall I bring anything?'],['See you at 3! ☕','Perfect, I’ll be there.','Coffee at 3 it is!'],['3 pm works well for me. See you at the café.','Thanks for organising. I’ll meet you there at 3.','Confirmed for 3 pm. Looking forward to catching up.']],
    project:[['Sounds good! I’ll take a look and get back to you.','Thanks for the update! Looking forward to seeing it.','That works for me. Let’s get it in the calendar.'],['Sounds good, thanks!','That works for me.','Will review and reply soon.'],['Thank you for the update. I’ll review this and follow up.','That timing works for me. Please send a calendar invitation.','Thanks for sharing. I’ll come back with my feedback shortly.']],
    family:[['That sounds lovely! Wouldn’t miss it 😊','Yes please! Can I bring anything along?','Already looking forward to it. See you Sunday!'],['Yes please! 😊','See you Sunday!','Would love to. What time?'],['Thank you for inviting me. Sunday works well.','I’d be happy to join. Please let me know the time.','Looking forward to lunch. Is there anything I can bring?']],
    delivery:[['Thanks for letting me know!','Great, I’ll keep an eye out. Thank you!','Thanks! I’ve received the update.'],['Thanks!','Got it, thank you.','Received, thanks.'],['Thank you for the delivery update.','Acknowledged, thank you.','Thank you for confirming.']],
    general:[['Thanks for the message! Great to hear from you.','Sounds good to me! Let’s catch up soon.','Of course, happy to help 😊'],['Sounds good!','Thanks for letting me know.','Let’s catch up soon.'],['Thank you for your message. I’ll get back to you shortly.','That works well for me. Thank you.','Happy to help. Please let me know what you need.']]
  };
  function ideas() {
    const sets = ideaSets[active()?.topic] || ideaSets.general;
    const toneIndex = ({friendly:0,concise:1,professional:2})[$('tone').value] || 0;
    const selected = sets[toneIndex];
    return selected.map((_,i) => selected[(i+suggestionOffset)%selected.length]);
  }
  function renderAssistant() {
    $('replyTools').hidden = assistantMode !== 'replies';
    $('generateButton').hidden = assistantMode !== 'replies' || !active();
    document.querySelectorAll('[data-assist]').forEach(el => el.classList.toggle('active',el.dataset.assist === assistantMode));
    if (!active()) { $('assistantOutput').innerHTML = '<p class="assistant-disabled-note">Select a conversation to explore sample replies.</p>'; return; }
    if (assistantMode === 'summary') {
      const c = active();
      const incoming = c.messages.filter(m => m.direction === 'in');
      $('assistantOutput').innerHTML = `<div class="summary-card"><h4>${icon('message')} Conversation at a glance</h4><p>${escape(c.name)} · ${c.messages.length} messages across ${new Set(c.messages.map(m => m.line)).size} line(s).</p><p><strong>Latest from ${escape(c.name.split(' ')[0])}</strong><br>${escape(incoming.at(-1)?.text || 'No incoming messages yet.')}</p><p><strong>Your latest reply</strong><br>${escape(c.messages.filter(m => m.direction === 'out').at(-1)?.text || 'You haven’t replied yet.')}</p><small>This is a local recap of your messages, not an AI-generated summary.</small></div>`;
      return;
    }
    $('assistantOutput').innerHTML = ideas().map((idea,i) => `<button class="reply-card" data-idea="${i}"><strong>${['KEEP IT NATURAL','ANOTHER WAY TO SAY IT','A LITTLE DIFFERENT'][i]}</strong><p>${escape(idea)}</p><span class="use-reply">Use this reply</span></button>`).join('');
  }
  function render() { renderLabels(); renderList(); renderThread(); renderAssistant(); }
  function chooseConversation(id) {
    const c = state.conversations.find(item => item.id === id); if (!c) return;
    selectedId = id; c.unread = 0; suggestionOffset = 0;
    $('appShell').classList.add('thread-open'); $('appShell').classList.remove('assistant-open');
    persist(); render();
  }
  function setView(next) {
    view = next;
    // Select before rendering so an empty folder cannot show an unrelated thread.
    const available = visibleConversations();
    if (!available.some(c => c.id === selectedId)) selectedId = available[0]?.id || null;
    $('appShell').classList.remove('thread-open','sidebar-open','assistant-open');
    render();
  }
  function updateComposer() {
    const value = $('messageInput').value;
    $('sendButton').disabled = !value.trim() || !active();
    $('rewriteButton').disabled = !value.trim();
    $('messageCounter').textContent = `SMS · ${value.length} character${value.length === 1 ? '' : 's'}`;
    if (selectedId) drafts.set(selectedId,value);
  }
  function send() {
    const c = active(), text = $('messageInput').value.trim();
    if (!c || !text) return;
    const line = $('sendLine').value;
    const sent = message(text,'out',Date.now(),line); sent.status = 'sending';
    c.messages.push(sent); c.line = line; c.archived = false; c.unread = 0;
    drafts.delete(c.id); draftLines.set(c.id,line); $('messageInput').value = '';
    if (view === 'archived' || view === 'unread') view = 'inbox';
    if (lineFilter !== 'all' && lineFilter !== line) lineFilter = 'all';
    persist(); render(); $('messageInput').focus();
    const advance = status => { const stored = state.conversations.find(item => item.id === c.id)?.messages.find(item => item.id === sent.id); if (!stored) return; stored.status = status; persist(); if (selectedId === c.id) renderMessages(); };
    setTimeout(() => advance('sent'),650); setTimeout(() => advance('delivered'),1400);
  }
  function openAssistant() {
    if (!state.settings.assistant) return;
    $('appShell').classList.add('assistant-open'); assistantMode = 'replies'; renderAssistant();
  }
  function openNew() { $('appShell').classList.remove('sidebar-open'); $('newForm').reset(); $('newLine').value = lineFilter === 'all' ? state.settings.defaultLine : lineFilter; $('newDialog').showModal(); }
  function openSettings() {
    $('appShell').classList.remove('sidebar-open');
    $('personalName').value = state.settings.personal; $('workName').value = state.settings.work;
    $('defaultLine').value = state.settings.defaultLine; $('enableAssistant').checked = state.settings.assistant;
    $('settingsDialog').showModal();
  }
  document.addEventListener('click',e => {
    const action = e.target.closest('[data-action]')?.dataset.action;
    if (action === 'new') openNew(); if (action === 'settings') openSettings();
    const close = e.target.closest('[data-close]')?.dataset.close;
    if (close) $(close).close();
    const conversation = e.target.closest('[data-conversation]');
    if (conversation) chooseConversation(conversation.dataset.conversation);
    const nav = e.target.closest('[data-view]'); if (nav) setView(nav.dataset.view);
    const filter = e.target.closest('[data-filter]')?.dataset.filter || e.target.closest('[data-line]')?.dataset.line;
    if (filter) { lineFilter = filter; setView(view); }
    const assist = e.target.closest('[data-assist]'); if (assist) { assistantMode = assist.dataset.assist; renderAssistant(); }
    const idea = e.target.closest('[data-idea]');
    if (idea) {
      $('messageInput').value = ideas()[Number(idea.dataset.idea)]; updateComposer();
      if (matchMedia('(max-width:1250px)').matches) $('appShell').classList.remove('assistant-open');
      $('messageInput').focus(); toast('Added to your draft. Make it yours.');
    }
    if (e.target.closest('#backButton')) { $('appShell').classList.remove('thread-open','assistant-open'); renderList(); }
    if (e.target.closest('#assistantToggle')) openAssistant();
    if (e.target.closest('#starButton') && active()) { active().starred = !active().starred; persist(); render(); }
    if (e.target.closest('#archiveButton') && active()) {
      const c = active(); c.archived = !c.archived; const archived = c.archived;
      if (archived) c.unread = 0;
      persist(); setView(view); toast(archived ? 'Conversation archived. Find it in Archived.' : 'Conversation restored to your inbox.');
    }
  });
  $('allLines').addEventListener('click',() => { lineFilter = 'all'; setView(view); });
  $('search').addEventListener('input',renderList);
  $('messageInput').addEventListener('input',updateComposer);
  $('messageInput').addEventListener('keydown',e => { if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); send(); } });
  $('sendButton').addEventListener('click',send);
  $('sendLine').addEventListener('change',() => { if (selectedId) draftLines.set(selectedId,$('sendLine').value); renderLineBar(); });
  $('quickSuggest').addEventListener('click',openAssistant);
  $('tone').addEventListener('change',() => { suggestionOffset = 0; renderAssistant(); });
  $('generateButton').addEventListener('click',() => { suggestionOffset++; renderAssistant(); toast('Showing another order of sample reply ideas.'); });
  $('closeAssistant').addEventListener('click',() => $('appShell').classList.remove('assistant-open'));
  $('menuButton').addEventListener('click',() => $('appShell').classList.toggle('sidebar-open'));
  $('sidebarScrim').addEventListener('click',() => $('appShell').classList.remove('sidebar-open'));
  $('rewriteButton').addEventListener('click',() => {
    const draft = $('messageInput').value.trim(); if (!draft) return;
    let polished = draft.replace(/\s+/g,' ').replace(/\bi\b/g,'I').replace(/\bim\b/gi,"I'm").replace(/\bthanks\b/gi,'Thank you');
    polished = polished.charAt(0).toUpperCase() + polished.slice(1);
    if (!/[.!?…😊☕]$/.test(polished)) polished += '.';
    $('messageInput').value = polished.slice(0,1600); updateComposer(); $('messageInput').focus(); toast('Draft tidied with local rules. Review before sending.');
  });
  $('newForm').addEventListener('submit',e => {
    e.preventDefault();
    const number = $('contactNumber').value.trim();
    if (number.replace(/\D/g,'').length < 6) { $('contactNumber').setCustomValidity('Please enter at least 6 digits.'); $('contactNumber').reportValidity(); return; }
    const existing = state.conversations.find(c => c.number.replace(/\D/g,'') === number.replace(/\D/g,''));
    $('newDialog').close(); $('search').value = ''; view = 'inbox'; lineFilter = 'all';
    if (existing) { existing.archived = false; draftLines.set(existing.id,$('newLine').value); chooseConversation(existing.id); return; }
    const name = $('contactName').value.trim() || number;
    const c = {id:uuid(),name,number,initials:initials(name),color:'#dce6d7',ink:'#6f8860',line:$('newLine').value,unread:0,starred:false,archived:false,topic:'general',messages:[]};
    state.conversations.push(c); chooseConversation(c.id); $('messageInput').focus();
  });
  $('contactNumber').addEventListener('input',() => $('contactNumber').setCustomValidity(''));
  $('settingsForm').addEventListener('submit',e => {
    e.preventDefault();
    if (!$('personalName').value.trim() || !$('workName').value.trim()) { toast('Please give both lines a name.'); return; }
    state.settings = {personal:$('personalName').value.trim(),work:$('workName').value.trim(),defaultLine:$('defaultLine').value,assistant:$('enableAssistant').checked};
    persist(); $('settingsDialog').close(); render(); toast('Your preferences are saved.');
  });
  $('simulateIncoming').addEventListener('click',() => {
    const c = state.conversations.find(item => item.id === 'maya') || state.conversations.find(item => !item.archived);
    if (!c) { toast('Start a conversation first to simulate a reply.'); return; }
    c.messages.push(message('Just checking in — hope your day is going well! 😊','in',Date.now(),c.line));
    c.unread = (c.unread || 0) + 1; c.archived = false;
    $('settingsDialog').close(); persist(); render(); toast(`Demo message received from ${c.name} on ${lineLabel(c.line)}.`);
  });
  $('resetDemo').addEventListener('click',() => { $('settingsDialog').close(); $('resetDialog').showModal(); });
  $('confirmReset').addEventListener('click',() => {
    state = initialState(); drafts.clear(); draftLines.clear(); selectedId = 'maya'; view = 'inbox'; lineFilter = 'all';
    $('search').value = ''; $('messageInput').value = ''; $('tone').value = 'friendly'; assistantMode = 'replies'; suggestionOffset = 0;
    $('appShell').classList.remove('thread-open','assistant-open','sidebar-open');
    $('resetDialog').close(); persist(); render(); toast('A fresh inbox, just for you.');
  });
  document.addEventListener('keydown',e => {
    if (document.querySelector('dialog[open]') || /INPUT|TEXTAREA|SELECT/.test(e.target.tagName) || e.ctrlKey || e.altKey || e.metaKey) return;
    if (e.key === '/') { e.preventDefault(); $('search').focus(); }
    if (e.key.toLowerCase() === 'n') { e.preventDefault(); openNew(); }
    if (e.key === 'Escape') $('appShell').classList.remove('assistant-open','sidebar-open');
  });
  render();
})();
