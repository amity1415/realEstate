"use strict";

const STAGES = ["NEW", "CONTACTED", "QUALIFIED", "VIEWING", "WON", "LOST"];
const state = { me: null, tz: "Asia/Kolkata", current: null };

/* ---------- utilities ---------- */

function $(sel) { return document.querySelector(sel); }
function el(tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
}
function esc(s) {
    return (s == null ? "" : String(s)).replace(/[&<>"']/g, c =>
        ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function getCookie(name) {
    const m = document.cookie.match("(^|;)\\s*" + name + "\\s*=\\s*([^;]+)");
    return m ? decodeURIComponent(m.pop()) : null;
}
function fmt(iso) {
    if (!iso) return "—";
    try {
        return new Intl.DateTimeFormat("en-GB",
            { dateStyle: "medium", timeStyle: "short", timeZone: state.tz }).format(new Date(iso));
    } catch (e) { return iso; }
}
function fmtDate(iso) {
    if (!iso) return "—";
    try {
        return new Intl.DateTimeFormat("en-GB",
            { dateStyle: "medium", timeZone: state.tz }).format(new Date(iso));
    } catch (e) { return iso; }
}
function money(v) {
    if (v == null || v === "") return "—";
    const n = Number(v);
    return isNaN(n) ? v : "AED " + n.toLocaleString("en-AE");
}
function bedroomsLabel(b) {
    if (b == null) return "";
    return b === 0 ? "Studio" : b + " BR";
}
function toast(msg) {
    const t = $("#toast");
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(t._timer);
    t._timer = setTimeout(() => { t.hidden = true; }, 2600);
}

/* ---------- API ---------- */

async function api(method, path, body, opts = {}) {
    const headers = {};
    const init = { method, headers, credentials: "same-origin" };
    if (body !== undefined) {
        headers["Content-Type"] = "application/json";
        init.body = JSON.stringify(body);
    }
    if (method !== "GET") {
        const token = getCookie("XSRF-TOKEN");
        if (token) headers["X-XSRF-TOKEN"] = token;
    }
    const res = await fetch("/api/v1" + path, init);
    if (res.status === 401) { showLogin(); throw new Error("Session expired — please sign in."); }
    if (!res.ok) {
        let msg = "Request failed (" + res.status + ")";
        try { const j = await res.json(); if (j.error && j.error.message) msg = j.error.message; }
        catch (e) { /* ignore */ }
        throw new Error(msg);
    }
    if (opts.raw) return res;
    const ct = res.headers.get("content-type") || "";
    return ct.includes("application/json") ? res.json() : res.text();
}

/* ---------- views ---------- */

function showLogin() {
    $("#login-view").hidden = false;
    $("#app-view").hidden = true;
    $("#drawer").hidden = true;
    $("#modal").hidden = true;
}
function showApp() {
    $("#login-view").hidden = true;
    $("#app-view").hidden = false;
}

async function init() {
    try {
        await fetch("/api/v1/auth/csrf", { credentials: "same-origin" });
        const me = await api("GET", "/auth/me");
        onAuthenticated(me);
    } catch (e) {
        showLogin();
    }
}

function onAuthenticated(me) {
    state.me = me;
    if (me.timezone) {
        state.tz = me.timezone;
        $("#tz-select").value = ["Asia/Kolkata", "Asia/Dubai"].includes(me.timezone)
            ? me.timezone : "Asia/Kolkata";
    }
    $("#ws-name").textContent = me.workspaceName || "";
    const nm = me.displayName || me.email || "";
    const uName = $("#user-name"); if (uName) uName.textContent = nm;
    const uRole = $("#user-role"); if (uRole) uRole.textContent = me.role || "";
    const uAv = $("#user-avatar"); if (uAv) uAv.textContent = (nm || "?").trim().charAt(0).toUpperCase();
    const admin = me.role === "OWNER" || me.role === "ADMIN";
    $("#btn-team").hidden = !admin;
    showApp();
    refresh();
    loadNotifCount();
    loadInboxCount();
    if (!state.notifTimer) state.notifTimer = setInterval(loadNotifCount, 60000);
    if (!state.inboxTimer) state.inboxTimer = setInterval(loadInboxCount, 60000);
}

async function refresh() {
    loadInboxCount();
    await Promise.all([loadMetrics(), loadBoard()]);
}

async function loadMetrics() {
    const box = $("#metrics");
    box.innerHTML = '<div class="skel skel-metric"></div>'.repeat(4);
    try {
        const m = await api("GET", "/metrics");
        const items = [
            ["Active leads", m.activeLeads, "accent-green",
                '<path d="M9 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 20a6 6 0 0 1 12 0M17.5 20a6 6 0 0 0-4.6-5.8"/>'],
            ["Open tasks", m.openTasks, "accent-blue",
                '<path d="M9 12l2.2 2.2L15.5 10M12 3a9 9 0 1 0 9 9"/>'],
            ["Overdue", m.overdueTasks, "accent-red",
                '<path d="M12 8v4.4l3 1.8M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z"/>'],
            ["Samples", m.sampleLeads, "accent-gold",
                '<path d="M3 7l9-4 9 4-9 4-9-4zM3 7v10l9 4 9-4V7"/>'],
        ];
        box.innerHTML = items.map(([lbl, num, acc, icon]) => `
            <div class="metric ${acc}">
                <div class="metric-ic"><svg viewBox="0 0 24 24" class="ic">${icon}</svg></div>
                <div><div class="num" data-to="${num}">0</div><div class="lbl">${esc(lbl)}</div></div>
            </div>`).join("");
        box.querySelectorAll(".num").forEach(animateCount);
    } catch (e) { box.innerHTML = ""; }
}

function animateCount(node) {
    const to = parseInt(node.getAttribute("data-to"), 10) || 0;
    if (to <= 0) { node.textContent = "0"; return; }
    const dur = 560, t0 = performance.now();
    (function step(t) {
        const p = Math.min(1, (t - t0) / dur);
        node.textContent = Math.round(to * (1 - Math.pow(1 - p, 3))).toLocaleString();
        if (p < 1) requestAnimationFrame(step);
    })(t0);
}

async function loadBoard() {
    const search = $("#search").value.trim();
    const includeArchived = $("#show-archived").checked;
    const showSamples = $("#show-samples").checked;
    const params = new URLSearchParams();
    params.set("size", "200");
    if (search) params.set("search", search);
    if (includeArchived) params.set("includeArchived", "true");
    if (!showSamples) params.set("sample", "false");

    const board = $("#board");
    board.innerHTML = boardSkeleton();
    let page;
    try { page = await api("GET", "/leads?" + params.toString()); }
    catch (e) { board.innerHTML = ""; return; }

    const byStage = {};
    STAGES.forEach(s => byStage[s] = []);
    page.content.forEach(l => { (byStage[l.stage] || (byStage[l.stage] = [])).push(l); });

    board.innerHTML = "";
    if (page.content.length === 0 && !search && !includeArchived) {
        board.appendChild(boardEmpty());
        return;
    }
    STAGES.forEach(stage => {
        const col = el("div", "column");
        col.dataset.stage = stage;
        col.addEventListener("dragover", e => {
            e.preventDefault(); e.dataTransfer.dropEffect = "move"; col.classList.add("drag-over");
        });
        col.addEventListener("dragleave", e => {
            if (!col.contains(e.relatedTarget)) col.classList.remove("drag-over");
        });
        col.addEventListener("drop", e => {
            e.preventDefault(); col.classList.remove("drag-over");
            const id = e.dataTransfer.getData("text/plain");
            if (id) moveLeadToStage(id, stage);
        });
        const head = el("div", "column-head");
        head.appendChild(el("span", "title", stage));
        head.appendChild(el("span", "count", String(byStage[stage].length)));
        col.appendChild(head);
        if (byStage[stage].length === 0) {
            col.appendChild(el("div", "col-empty", "Drop a lead here"));
        } else {
            byStage[stage].forEach(l => col.appendChild(leadCard(l)));
        }
        board.appendChild(col);
    });
}

async function moveLeadToStage(leadId, newStage) {
    try {
        const lead = await api("GET", "/leads/" + leadId);
        if (lead.stage === newStage) return;
        const req = lead.requirement || {};
        await api("PATCH", "/leads/" + leadId, {
            name: lead.name, email: lead.email, phone: lead.phone, source: lead.source,
            stage: newStage, purpose: lead.purpose, budgetAed: lead.budgetAed,
            bedrooms: lead.bedrooms, emirate: lead.emirate, community: lead.community,
            purchaseTimeframe: lead.purchaseTimeframe, statedIntent: lead.statedIntent,
            stageReason: "Moved on board",
            requirement: { mustHaveAmenities: req.mustHaveAmenities || [], preferences: req.preferences || {} },
            version: lead.version,
        });
        toast(lead.name + " → " + newStage);
        await refresh();
    } catch (e) { toast(e.message); await loadBoard(); }
}

function boardSkeleton() {
    const col = '<div class="column"><div class="skel skel-head"></div>'
        + '<div class="skel skel-card"></div><div class="skel skel-card"></div></div>';
    return col.repeat(6);
}

function boardEmpty() {
    const wrap = el("div", "board-empty");
    wrap.innerHTML = `<div class="board-empty-inner">
        <svg viewBox="0 0 24 24" class="ic"><path d="M3 7l9-4 9 4-9 4-9-4zM3 7v10l9 4 9-4V7M12 11v10"/></svg>
        <h3>No leads yet</h3>
        <p>Add your first buyer, or import an existing backup, to start building your pipeline.</p>
        <button class="btn btn-primary" id="empty-new-lead">+ Add your first lead</button></div>`;
    wrap.querySelector("#empty-new-lead").addEventListener("click", openNewLead);
    return wrap;
}

function leadCard(l) {
    const card = el("div", "card");
    card.tabIndex = 0;
    card.setAttribute("role", "button");
    card.draggable = true;
    card.dataset.leadId = l.id;
    card.dataset.stage = l.stage;
    card.addEventListener("dragstart", e => {
        e.dataTransfer.setData("text/plain", l.id);
        e.dataTransfer.effectAllowed = "move";
        card.classList.add("dragging");
    });
    card.addEventListener("dragend", () => card.classList.remove("dragging"));

    const top = el("div", "card-top");
    const av = el("div", "card-av", initials(l.name));
    av.style.background = avatarColor(l.name);
    top.appendChild(av);
    top.appendChild(el("div", "name", l.name));
    if (l.score != null && l.segment) top.appendChild(scoreChip(l.score, l.segment));
    card.appendChild(top);

    const meta = el("div", "meta");
    if (l.budgetAed != null) meta.appendChild(el("span", null, money(l.budgetAed)));
    if (l.bedrooms != null) meta.appendChild(el("span", null, bedroomsLabel(l.bedrooms)));
    if (l.emirate) meta.appendChild(el("span", null, l.emirate));
    if (meta.children.length) card.appendChild(meta);

    const foot = el("div", "card-foot");
    foot.appendChild(el("span", "card-time", l.updatedAt ? timeAgo(l.updatedAt) : ""));
    const badges = el("div", "meta badges");
    if (l.sample) badges.appendChild(el("span", "badge badge-sample", "Sample"));
    if (l.archivedAt) badges.appendChild(el("span", "badge badge-archived", "Archived"));
    foot.appendChild(badges);
    card.appendChild(foot);

    card.addEventListener("click", () => openLead(l.id));
    card.addEventListener("keydown", e => {
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openLead(l.id); }
    });
    return card;
}

const SEGMENTS = {
    HOT:     { label: "Hot",     color: "#b23b2e", bg: "#fbeae7", on: "#8f2d22" },
    WARM:    { label: "Warm",    color: "#9a6b16", bg: "#f7eecf", on: "#7d5f14" },
    NURTURE: { label: "Nurture", color: "#2f67d6", bg: "#e6eefb", on: "#2a4ea8" },
    COLD:    { label: "Cold",    color: "#6d7c73", bg: "#eceeea", on: "#51605a" },
};

function scoreChip(score, segment) {
    const s = SEGMENTS[segment] || SEGMENTS.COLD;
    const chip = el("span", "score-chip", String(score));
    chip.style.background = s.bg;
    chip.style.color = s.on;
    chip.title = s.label + " lead · score " + score + "/100";
    return chip;
}

function initials(name) {
    const p = (name || "?").trim().split(/\s+/);
    return ((p[0] || "?").charAt(0) + (p[1] ? p[1].charAt(0) : "")).toUpperCase();
}
const AV_COLORS = ["#166a49", "#2f67d6", "#7c5cfc", "#b8862f", "#1c855b", "#b2544a", "#0e7490", "#8a3ffc"];
function avatarColor(name) {
    let h = 0; const s = name || "?";
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
    return AV_COLORS[h % AV_COLORS.length];
}
function timeAgo(iso) {
    const diff = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
    if (diff < 60) return "just now";
    if (diff < 3600) return Math.floor(diff / 60) + "m ago";
    if (diff < 86400) return Math.floor(diff / 3600) + "h ago";
    if (diff < 604800) return Math.floor(diff / 86400) + "d ago";
    return fmtDate(iso);
}

/* ---------- view switching + analytics ---------- */

const STAGE_COLORS = { NEW: "#64748b", CONTACTED: "#3b82f6", QUALIFIED: "#7c5cfc",
    VIEWING: "#c49a46", WON: "#1c855b", LOST: "#b2544a" };

function showView(view) {
    const titles = { board: "Pipeline", analytics: "Analytics", hot: "Hot leads", inbox: "Inbox" };
    const isBoard = view === "board";
    $("#board").hidden = !isBoard;
    $("#metrics").hidden = !isBoard;
    $("#analytics").hidden = view !== "analytics";
    $("#hot").hidden = view !== "hot";
    $("#inbox").hidden = view !== "inbox";
    const tb = document.querySelector(".toolbar"); if (tb) tb.hidden = !isBoard;
    $("#page-title").textContent = titles[view] || "Pipeline";
    $("#nav-pipeline").classList.toggle("is-active", isBoard);
    $("#nav-analytics").classList.toggle("is-active", view === "analytics");
    const nh = $("#nav-hot"); if (nh) nh.classList.toggle("is-active", view === "hot");
    const ni = $("#nav-inbox"); if (ni) ni.classList.toggle("is-active", view === "inbox");
    if (view === "analytics") loadAnalytics(state.analyticsDays || 90);
    else if (view === "hot") loadHot(state.hotSegment || "");
    else if (view === "inbox") loadInbox();
}

async function loadInboxCount() {
    try {
        const page = await api("GET", "/leads/inbox?size=1");
        const n = page.totalElements || 0;
        const badge = $("#inbox-count");
        if (badge) { badge.textContent = n > 99 ? "99+" : String(n); badge.hidden = n === 0; }
    } catch (e) { /* non-fatal */ }
}

async function loadInbox() {
    const box = $("#inbox");
    box.innerHTML = `<div class="hot-head">
        <div><h2>Inbox</h2>
            <p class="muted">New leads awaiting first response — newest first, with where each came from. Reach out fast; speed-to-lead wins deals.</p></div></div>
        <div class="hot-list">
            <div class="skel" style="height:72px;border-radius:14px;margin-bottom:10px"></div>
            <div class="skel" style="height:72px;border-radius:14px;margin-bottom:10px"></div>
            <div class="skel" style="height:72px;border-radius:14px"></div></div>`;
    let page;
    try { page = await api("GET", "/leads/inbox?size=100"); }
    catch (e) { box.querySelector(".hot-list").innerHTML = `<p class="form-error">${esc(e.message)}</p>`; return; }

    const list = box.querySelector(".hot-list");
    list.innerHTML = "";
    if (!page.content.length) {
        list.innerHTML = `<div class="hot-empty"><p>Inbox zero. 🎉</p>
            <p class="muted">New leads from your website, marketing campaigns and manual entry land here for triage.</p></div>`;
        return;
    }
    page.content.forEach(l => list.appendChild(inboxRow(l)));
}

function inboxRow(l) {
    const row = el("div", "hot-row inbox-row");
    row.tabIndex = 0;
    row.setAttribute("role", "button");

    const av = el("div", "card-av", initials(l.name));
    av.style.background = avatarColor(l.name);
    row.appendChild(av);

    const mid = el("div", "hot-mid");
    const nameRow = el("div", "hot-name-row");
    nameRow.appendChild(el("span", "hot-name", l.name));
    if (l.score != null && l.segment) nameRow.appendChild(scoreChip(l.score, l.segment));
    nameRow.appendChild(el("span", "card-time", l.createdAt ? timeAgo(l.createdAt) : ""));
    mid.appendChild(nameRow);

    const meta = el("div", "hot-meta");
    const chan = l.channel || l.source;
    if (chan) { const c = el("span", "chan-chip"); c.textContent = chan; meta.appendChild(c); }
    if (l.utmCampaign) meta.appendChild(el("span", null, l.utmCampaign));
    if (l.email) meta.appendChild(el("span", null, l.email));
    if (l.phone) meta.appendChild(el("span", null, l.phone));
    if (meta.children.length) mid.appendChild(meta);
    row.appendChild(mid);

    const act = el("button", "btn btn-ghost btn-sm", "Mark contacted");
    act.addEventListener("click", async (e) => {
        e.stopPropagation();
        act.disabled = true;
        await moveLeadToStage(l.id, "CONTACTED");
        loadInbox(); loadInboxCount();
    });
    row.appendChild(act);

    row.addEventListener("click", () => openLead(l.id));
    row.addEventListener("keydown", e => {
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openLead(l.id); }
    });
    return row;
}

async function loadHot(segment) {
    state.hotSegment = segment || "";
    const box = $("#hot");
    const filters = ["", "HOT", "WARM", "NURTURE"];
    const chips = filters.map(f => {
        const active = (state.hotSegment || "") === f ? " is-active" : "";
        const label = f ? (SEGMENTS[f] ? SEGMENTS[f].label : f) : "All";
        return `<button class="hot-filter${active}" data-seg="${f}">${label}</button>`;
    }).join("");
    box.innerHTML = `<div class="hot-head">
        <div><h2>Hot leads</h2>
            <p class="muted">Highest-intent buyers first, with the reasons behind each score. Transparent, rule-based — no black box.</p></div>
        <div class="hot-filters">${chips}</div></div>
        <div class="hot-list">
            <div class="skel" style="height:72px;border-radius:14px;margin-bottom:10px"></div>
            <div class="skel" style="height:72px;border-radius:14px;margin-bottom:10px"></div>
            <div class="skel" style="height:72px;border-radius:14px"></div></div>`;
    box.querySelectorAll(".hot-filter").forEach(b =>
        b.addEventListener("click", () => loadHot(b.dataset.seg)));

    const q = new URLSearchParams();
    q.set("size", "100");
    if (state.hotSegment) q.set("segment", state.hotSegment);
    let page;
    try { page = await api("GET", "/leads/hot?" + q.toString()); }
    catch (e) { box.querySelector(".hot-list").innerHTML = `<p class="form-error">${esc(e.message)}</p>`; return; }

    const list = box.querySelector(".hot-list");
    list.innerHTML = "";
    if (!page.content.length) {
        list.innerHTML = `<div class="hot-empty">
            <p>No ${state.hotSegment ? (SEGMENTS[state.hotSegment] ? SEGMENTS[state.hotSegment].label.toLowerCase() : "") + " " : ""}leads yet.</p>
            <p class="muted">Scores update automatically as you log calls, viewings and messages.</p></div>`;
        return;
    }
    page.content.forEach((l, i) => list.appendChild(hotRow(l, i + 1)));
}

function hotRow(l, rank) {
    const s = SEGMENTS[l.segment] || SEGMENTS.COLD;
    const row = el("div", "hot-row");
    row.tabIndex = 0;
    row.setAttribute("role", "button");

    row.appendChild(el("div", "hot-rank", "#" + rank));

    const ring = el("div", "hot-score");
    ring.style.background = s.bg;
    ring.style.color = s.on;
    ring.innerHTML = `<strong>${l.score}</strong><span>${s.label}</span>`;
    row.appendChild(ring);

    const mid = el("div", "hot-mid");
    const nameRow = el("div", "hot-name-row");
    const av = el("span", "card-av", initials(l.name));
    av.style.background = avatarColor(l.name);
    nameRow.appendChild(av);
    nameRow.appendChild(el("span", "hot-name", l.name));
    nameRow.appendChild(el("span", "hot-stage", l.stage));
    mid.appendChild(nameRow);

    const meta = el("div", "hot-meta");
    if (l.budgetAed != null) meta.appendChild(el("span", null, money(l.budgetAed)));
    if (l.bedrooms != null) meta.appendChild(el("span", null, bedroomsLabel(l.bedrooms)));
    if (l.emirate) meta.appendChild(el("span", null, l.emirate));
    if (l.email) meta.appendChild(el("span", null, l.email));
    if (meta.children.length) mid.appendChild(meta);

    const factors = Array.isArray(l.factors) ? l.factors : [];
    if (factors.length) {
        const why = el("div", "hot-why");
        factors.forEach(f => {
            const chip = el("span", "why-chip");
            chip.innerHTML = `${esc(String(f.factor))} <b>+${esc(String(f.points))}</b>`;
            why.appendChild(chip);
        });
        mid.appendChild(why);
    }
    row.appendChild(mid);

    const open = () => openLead(l.id);
    row.addEventListener("click", open);
    row.addEventListener("keydown", e => {
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); open(); }
    });
    return row;
}

async function loadAnalytics(days) {
    state.analyticsDays = days;
    const box = $("#analytics");
    box.innerHTML = `<div class="analytics-head">
        <div><h2>Analytics</h2><p class="muted">Pipeline health &amp; cohort conversion.</p></div>
        <select id="an-range" class="select">
            <option value="30">Last 30 days</option>
            <option value="90">Last 90 days</option>
            <option value="365">Last 12 months</option>
        </select></div>
        <div class="an-grid">
            <div class="skel" style="height:112px;border-radius:15px"></div>
            <div class="skel" style="height:260px;border-radius:15px"></div>
            <div class="skel" style="height:260px;border-radius:15px"></div></div>`;
    const sel = box.querySelector("#an-range");
    sel.value = String(days);
    sel.addEventListener("change", () => loadAnalytics(parseInt(sel.value, 10)));
    const to = new Date().toISOString();
    const from = new Date(Date.now() - days * 86400000).toISOString();
    try {
        const [m, conv] = await Promise.all([
            api("GET", "/metrics"),
            api("GET", "/metrics/conversion?from=" + encodeURIComponent(from) + "&to=" + encodeURIComponent(to)),
        ]);
        renderAnalytics(m, conv, days);
    } catch (e) {
        box.querySelector(".an-grid").innerHTML = `<p class="form-error">${esc(e.message)}</p>`;
    }
}

function renderAnalytics(m, conv, days) {
    const grid = $("#analytics").querySelector(".an-grid");
    const cohort = conv.cohortSize || 0;
    const rateOf = s => conv.conversionRateByStage ? conv.conversionRateByStage[s] : null;
    const reachOf = s => (conv.reachedByStage && conv.reachedByStage[s]) || 0;
    const wonRate = rateOf("WON");

    const figures = [
        ["Active leads", m.activeLeads],
        ["Cohort · " + days + "d", cohort],
        ["Won in cohort", reachOf("WON")],
        ["Win rate", wonRate == null ? "—" : Math.round(wonRate * 100) + "%"],
        ["Open tasks", m.openTasks],
        ["Overdue", m.overdueTasks],
    ];
    const figuresHtml = figures.map(([l, v]) =>
        `<div class="an-fig"><div class="an-fig-num">${esc(String(v))}</div><div class="an-fig-lbl">${esc(l)}</div></div>`).join("");

    const funnel = STAGES.map(stage => {
        const reached = reachOf(stage), rate = rateOf(stage);
        const pct = rate == null ? null : Math.round(rate * 100);
        const w = cohort ? Math.max(reached ? 3 : 0, (reached / cohort) * 100) : 0;
        return `<div class="funnel-row">
            <div class="funnel-label"><span class="dot" style="background:${STAGE_COLORS[stage]}"></span>${stage}</div>
            <div class="funnel-track"><div class="funnel-bar" style="width:${w}%;background:${STAGE_COLORS[stage]}"></div></div>
            <div class="funnel-val">${reached}${pct == null ? "" : `<span class="funnel-pct">${pct}%</span>`}</div>
        </div>`;
    }).join("");

    const counts = m.currentStageCounts || {};
    const maxC = Math.max(1, ...STAGES.map(s => counts[s] || 0));
    const dist = STAGES.map(stage => {
        const c = counts[stage] || 0, w = (c / maxC) * 100;
        return `<div class="funnel-row">
            <div class="funnel-label"><span class="dot" style="background:${STAGE_COLORS[stage]}"></span>${stage}</div>
            <div class="funnel-track"><div class="funnel-bar" style="width:${Math.max(c ? 4 : 0, w)}%;background:${STAGE_COLORS[stage]}"></div></div>
            <div class="funnel-val">${c}</div></div>`;
    }).join("");

    grid.innerHTML = `
        <div class="an-card an-figures">${figuresHtml}</div>
        <div class="an-card">
            <h3>Conversion funnel</h3>
            <p class="an-note">Of ${cohort} lead${cohort === 1 ? "" : "s"} that entered in the last ${days} days, how many reached each stage.</p>
            ${cohort ? funnel : '<p class="muted">No leads entered the pipeline in this window yet.</p>'}
        </div>
        <div class="an-card">
            <h3>Current pipeline</h3>
            <p class="an-note">Active leads by stage right now — a snapshot, not a conversion rate.</p>
            ${dist}
        </div>`;
    grid.querySelectorAll(".funnel-bar").forEach(b => {
        const w = b.style.width; b.style.width = "0"; requestAnimationFrame(() => { b.style.width = w; });
    });
}

/* ---------- lead detail ---------- */

async function openLead(id) {
    try {
        const [lead, interactions, tasks, matches, coaching] = await Promise.all([
            api("GET", "/leads/" + id),
            api("GET", "/leads/" + id + "/interactions?size=50"),
            api("GET", "/tasks?leadId=" + id + "&size=50"),
            api("GET", "/leads/" + id + "/property-matches?limit=3"),
            api("GET", "/leads/" + id + "/coaching-runs"),
        ]);
        state.current = lead;
        renderDrawer(lead, interactions.content, tasks.content, matches,
            Array.isArray(coaching) && coaching.length ? coaching[0] : null);
        $("#drawer").hidden = false;
    } catch (e) { toast(e.message); }
}

function renderDrawer(lead, interactions, tasks, matches, latestCoaching) {
    $("#drawer-title").textContent = lead.name + (lead.sample ? "  (sample)" : "");
    const body = $("#drawer-body");
    body.innerHTML = "";

    // AI coach
    const coachSec = el("div", "section coach-section");
    coachSec.innerHTML = `
        <h3>AI sales coach</h3>
        <div class="row" style="gap:8px;flex-wrap:wrap">
            <button class="btn btn-primary btn-sm" id="btn-coach">Coach this lead</button>
            <select id="coach-lang" title="Draft language">
                <option value="en">English</option>
                <option value="ar">Arabic</option>
            </select>
            <span class="muted">On-demand. Nothing is sent automatically.</span>
        </div>
        <div id="coach-out"></div>`;
    body.appendChild(coachSec);
    renderCoaching(latestCoaching);

    // Edit form
    const form = el("div", "section");
    form.innerHTML = `
        <h3>Lead details</h3>
        <div class="grid2">
            <label>Name<input id="f-name" value="${esc(lead.name)}"></label>
            <label>Stage<select id="f-stage">${STAGES.map(s =>
                `<option value="${s}" ${s === lead.stage ? "selected" : ""}>${s}</option>`).join("")}</select></label>
            <label>Email<input id="f-email" value="${esc(lead.email)}"></label>
            <label>Phone<input id="f-phone" value="${esc(lead.phone)}"></label>
            <label>Budget (AED)<input id="f-budget" type="number" value="${lead.budgetAed ?? ""}"></label>
            <label>Bedrooms<input id="f-bedrooms" type="number" value="${lead.bedrooms ?? ""}"></label>
            <label>Emirate<input id="f-emirate" value="${esc(lead.emirate)}"></label>
            <label>Community<input id="f-community" value="${esc(lead.community)}"></label>
            <label>Source<input id="f-source" value="${esc(lead.source)}"></label>
            <label>Purchase timeframe<input id="f-timeframe" value="${esc(lead.purchaseTimeframe)}"></label>
        </div>
        <label>Purpose<input id="f-purpose" value="${esc(lead.purpose)}"></label>
        <label>Stated intent<textarea id="f-intent">${esc(lead.statedIntent)}</textarea></label>
        <label>Must-have amenities (comma separated)
            <input id="f-amenities" value="${esc((lead.requirement && lead.requirement.mustHaveAmenities || []).join(', '))}"></label>
        <label>Reason for stage change (optional)<input id="f-stage-reason" placeholder="e.g. Qualified after call"></label>
        <div class="row" style="margin-top:10px">
            <button class="btn btn-primary" id="btn-save-lead">Save changes</button>
            ${lead.archivedAt
                ? `<button class="btn btn-ghost" id="btn-restore-lead">Restore</button>`
                : `<button class="btn btn-danger" id="btn-archive-lead">Archive</button>`}
            <span class="muted">v${lead.version} · updated ${esc(fmt(lead.updatedAt))}</span>
        </div>`;
    body.appendChild(form);

    // Property matches
    const matchSec = el("div", "section");
    matchSec.appendChild(el("h3", null, "Property matches (deterministic)"));
    if (!matches.length) {
        matchSec.appendChild(el("p", "muted",
            "No eligible matches. Matches enforce budget, emirate, bedrooms and must-have amenities, and exclude samples."));
    } else {
        matches.forEach(m => matchSec.appendChild(matchCard(m)));
        matchSec.appendChild(el("p", "muted", "Sample inventory is never shown here."));
    }
    body.appendChild(matchSec);

    // Interactions
    const intSec = el("div", "section");
    intSec.appendChild(el("h3", null, "Interactions"));
    const intForm = el("div");
    intForm.innerHTML = `
        <div class="row">
            <select id="i-type">${["NOTE","CALL","MESSAGE","EMAIL","MEETING","VIEWING","OTHER"]
                .map(t => `<option>${t}</option>`).join("")}</select>
            <input id="i-text" placeholder="What happened / what the buyer said" style="flex:1">
            <button class="btn btn-primary btn-sm" id="btn-add-int">Add</button>
        </div>`;
    intSec.appendChild(intForm);
    const intList = el("div");
    if (!interactions.length) intList.appendChild(el("p", "muted", "No interactions recorded."));
    interactions.forEach(i => {
        const row = el("div", "list-item");
        row.innerHTML = `<strong>${esc(i.type)}</strong> · <span class="muted">${esc(fmt(i.occurredAt))}</span><br>${esc(i.text)}`;
        intList.appendChild(row);
    });
    intSec.appendChild(intList);
    body.appendChild(intSec);

    // Tasks
    const taskSec = el("div", "section");
    taskSec.appendChild(el("h3", null, "Follow-up tasks"));
    const taskForm = el("div");
    taskForm.innerHTML = `
        <div class="row">
            <input id="t-action" placeholder="Next action" style="flex:1">
            <input id="t-due" type="datetime-local">
            <button class="btn btn-primary btn-sm" id="btn-add-task">Add</button>
        </div>`;
    taskSec.appendChild(taskForm);
    const taskList = el("div");
    if (!tasks.length) taskList.appendChild(el("p", "muted", "No tasks."));
    tasks.forEach(t => {
        const row = el("div", "list-item");
        const done = t.status === "DONE";
        row.innerHTML = `<span style="${done ? 'text-decoration:line-through;color:#8a8' : ''}">
            ${esc(t.action)}</span> · <span class="muted">due ${esc(t.dueAt ? fmt(t.dueAt) : '—')} · ${esc(t.status)}</span>`;
        if (!done) {
            const b = el("button", "btn btn-ghost btn-sm", "Done");
            b.style.marginLeft = "8px";
            b.addEventListener("click", async () => {
                try { await api("PATCH", "/tasks/" + t.id, { status: "DONE" }); openLead(state.current.id); }
                catch (e) { toast(e.message); }
            });
            row.appendChild(b);
        }
        taskList.appendChild(row);
    });
    taskSec.appendChild(taskList);
    body.appendChild(taskSec);

    // Assignment & outreach (outbox)
    const outreach = el("div", "section");
    outreach.innerHTML = `<h3>Assignment & outreach</h3>
        <div class="row" id="assign-row" style="margin-bottom:10px"></div>
        <div id="outbox-compose"></div>
        <div id="outbox-list"><p class="muted">Loading messages…</p></div>`;
    body.appendChild(outreach);
    renderOutreach(lead);

    // wire handlers
    $("#btn-save-lead").addEventListener("click", saveLead);
    const archiveBtn = $("#btn-archive-lead");
    if (archiveBtn) archiveBtn.addEventListener("click", archiveLead);
    const restoreBtn = $("#btn-restore-lead");
    if (restoreBtn) restoreBtn.addEventListener("click", restoreLead);
    $("#btn-add-int").addEventListener("click", addInteraction);
    $("#btn-add-task").addEventListener("click", addTask);
    $("#btn-coach").addEventListener("click", generateCoaching);
}

async function generateCoaching() {
    const lead = state.current;
    const btn = $("#btn-coach");
    btn.disabled = true;
    btn.textContent = "Coaching…";
    try {
        const key = "coach-" + lead.id + "-" + Date.now();
        const res = await fetch("/api/v1/leads/" + lead.id + "/coaching-runs", {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": getCookie("XSRF-TOKEN") || "",
                "Idempotency-Key": key,
            },
            body: JSON.stringify({ language: $("#coach-lang").value }),
        });
        if (res.status === 401) { showLogin(); return; }
        if (!res.ok) { const j = await res.json().catch(() => ({})); throw new Error(j.error?.message || "Coaching failed"); }
        renderCoaching(await res.json());
    } catch (e) {
        toast(e.message);
    } finally {
        btn.disabled = false;
        btn.textContent = "Coach this lead";
    }
}

function renderCoaching(run) {
    const out = $("#coach-out");
    if (!out) return;
    if (!run) { out.innerHTML = `<p class="muted">No coaching yet. Generate an evidence-backed next step.</p>`; return; }

    if (run.status !== "COMPLETED" || !run.result) {
        const msg = run.errorMessage || (run.status === "UNAVAILABLE"
            ? "Coaching is currently unavailable." : "Coaching did not complete.");
        out.innerHTML = `<div class="coach-card"><p class="trade">△ ${esc(msg)}</p>
            <p class="muted">No result is shown rather than a fabricated one. You can try again.</p></div>`;
        return;
    }

    const r = run.result;
    const badge = run.live
        ? `<span class="badge badge-live">Live · ${esc(run.provider)}/${esc(run.model)}</span>`
        : `<span class="badge badge-mock">Mock — not a live LLM</span>`;
    const outdated = run.outdated
        ? `<span class="badge badge-archived">Outdated — regenerate</span>` : "";

    const facts = (r.facts || []).map(f =>
        `<li>${esc(f.text)} <span class="evref">[${(f.interactionIds || []).length} evidence]</span></li>`).join("");
    const objs = (r.objections || []).map(o =>
        `<li><strong>${esc(o.category)}</strong>${o.stated ? " (stated)" : " (inferred)"}
         <span class="evref">[${(o.interactionIds || []).length} evidence]</span></li>`).join("");
    const hyps = (r.hypotheses || []).map(h =>
        `<li>${esc(h.text)} <span class="muted">· ${esc(h.confidence)} confidence</span></li>`).join("");
    const intents = (r.intentSignals || []).map(s => `<li>${esc(s.text)}</li>`).join("");
    const unknowns = (r.unknowns || []).map(u => `<li>${esc(u)}</li>`).join("");
    const questions = (r.nextAction?.questions || []).map(q => `<li>${esc(q)}</li>`).join("");
    const warnings = (r.warnings || []).map(w => `<div class="trade">△ ${esc(w)}</div>`).join("");

    let matchesHtml = "";
    if (r.propertyMatches && r.propertyMatches.length) {
        matchesHtml = `<div class="coach-block"><h4>Eligible matches (deterministic)</h4>${
            r.propertyMatches.map(m => {
                const p = m.property;
                const title = [p.project, p.unitType, bedroomsLabel(p.bedrooms)].filter(Boolean).join(" · ");
                return `<div class="match"><div class="name">${esc(title || "Property")}</div>
                    <div class="meta"><span>${esc(money(p.priceAed))}</span>
                    <span>${esc(p.emirate || "")}</span></div>
                    ${(m.fit || []).map(f => `<div class="fit">✓ ${esc(f)}</div>`).join("")}
                    ${(m.tradeOffs || []).map(t => `<div class="trade">△ ${esc(t)}</div>`).join("")}</div>`;
            }).join("")}</div>`;
    }

    out.innerHTML = `
        <div class="coach-card">
            <div class="row" style="gap:8px;flex-wrap:wrap;margin-bottom:8px">${badge} ${outdated}
                <span class="muted">prompt ${esc(run.promptVersion)} · ${esc(fmt(run.createdAt))}</span></div>
            <p><strong>${esc(r.summary || "")}</strong></p>

            <div class="coach-block"><h4>Next action — ${esc(r.nextAction?.type || "")}</h4>
                <p>${esc(r.nextAction?.rationale || "")}</p>
                ${questions ? `<ul>${questions}</ul>` : ""}
                ${r.alternativeApproach ? `<p class="muted">If declined: ${esc(r.alternativeApproach)}</p>` : ""}</div>

            ${facts ? `<div class="coach-block"><h4>Confirmed facts</h4><ul>${facts}</ul></div>` : ""}
            ${objs ? `<div class="coach-block"><h4>Objections</h4><ul>${objs}</ul></div>` : ""}
            ${hyps ? `<div class="coach-block"><h4>Hypotheses (inferred)</h4><ul>${hyps}</ul></div>` : ""}
            ${intents ? `<div class="coach-block"><h4>Intent signals</h4><ul>${intents}</ul></div>` : ""}
            ${unknowns ? `<div class="coach-block"><h4>Missing information</h4><ul>${unknowns}</ul></div>` : ""}
            ${matchesHtml}

            <div class="coach-block"><h4>Follow-up draft (editable · not sent)</h4>
                <textarea id="coach-draft" style="min-height:92px">${esc(r.messageDraft || "")}</textarea>
                <div class="row" style="gap:8px;margin-top:6px">
                    <button class="btn btn-ghost btn-sm" id="btn-copy-draft">Copy</button>
                    <span class="muted">Follow-up: ${esc(r.followUp?.basis || "SUGGESTED")} — ${esc(r.followUp?.reason || "")}</span>
                </div></div>

            ${warnings ? `<div class="coach-block"><h4>Warnings</h4>${warnings}</div>` : ""}

            <div class="coach-block"><h4>Was this helpful?</h4>
                <div class="row" style="gap:8px;flex-wrap:wrap">
                    <button class="btn btn-ghost btn-sm" data-fb="yes">👍 Helpful</button>
                    <button class="btn btn-ghost btn-sm" data-fb="no">👎 Not helpful</button>
                    <input id="fb-comment" placeholder="Optional comment" style="flex:1;min-width:160px">
                </div></div>
        </div>`;

    const copyBtn = $("#btn-copy-draft");
    if (copyBtn) copyBtn.addEventListener("click", () => {
        const draft = $("#coach-draft").value;
        navigator.clipboard?.writeText(draft).then(() => toast("Draft copied")).catch(() => toast("Copy failed"));
    });
    out.querySelectorAll("[data-fb]").forEach(b => b.addEventListener("click", () =>
        submitCoachFeedback(run.id, b.getAttribute("data-fb") === "yes")));
}

async function submitCoachFeedback(runId, helpful) {
    try {
        await api("POST", "/coaching-runs/" + runId + "/feedback", {
            helpful,
            comment: ($("#fb-comment") && $("#fb-comment").value.trim()) || null,
        });
        toast("Thanks for the feedback");
    } catch (e) { toast(e.message); }
}

function matchCard(m) {
    const p = m.property;
    const card = el("div", "match");
    const title = [p.project, p.unitType, bedroomsLabel(p.bedrooms)].filter(Boolean).join(" · ");
    card.appendChild(el("div", "name", title || "Property"));
    const meta = el("div", "meta");
    meta.appendChild(el("span", null, money(p.priceAed)));
    if (p.emirate) meta.appendChild(el("span", null, p.emirate));
    if (p.community) meta.appendChild(el("span", null, p.community));
    card.appendChild(meta);
    (m.fit || []).forEach(f => card.appendChild(el("div", "fit", "✓ " + f)));
    (m.tradeOffs || []).forEach(t => card.appendChild(el("div", "trade", "△ " + t)));
    return card;
}

async function saveLead() {
    const lead = state.current;
    const amenities = $("#f-amenities").value.split(",").map(s => s.trim()).filter(Boolean);
    const payload = {
        name: $("#f-name").value.trim(),
        email: nullable($("#f-email").value),
        phone: nullable($("#f-phone").value),
        source: nullable($("#f-source").value),
        stage: $("#f-stage").value,
        purpose: nullable($("#f-purpose").value),
        budgetAed: numberOrNull($("#f-budget").value),
        bedrooms: intOrNull($("#f-bedrooms").value),
        emirate: nullable($("#f-emirate").value),
        community: nullable($("#f-community").value),
        purchaseTimeframe: nullable($("#f-timeframe").value),
        statedIntent: nullable($("#f-intent").value),
        stageReason: nullable($("#f-stage-reason").value),
        requirement: { mustHaveAmenities: amenities, preferences: lead.requirement ? lead.requirement.preferences : {} },
        version: lead.version,
    };
    try {
        await api("PATCH", "/leads/" + lead.id, payload);
        toast("Saved");
        await openLead(lead.id);
        await refresh();
    } catch (e) { toast(e.message); }
}

async function archiveLead() {
    if (!confirm("Archive this lead? It can be restored later.")) return;
    try {
        await api("POST", "/leads/" + state.current.id + "/archive");
        toast("Archived");
        $("#drawer").hidden = true;
        await refresh();
    } catch (e) { toast(e.message); }
}
async function restoreLead() {
    try {
        await api("POST", "/leads/" + state.current.id + "/restore");
        toast("Restored");
        await openLead(state.current.id);
        await refresh();
    } catch (e) { toast(e.message); }
}

async function addInteraction() {
    const text = $("#i-text").value.trim();
    if (!text) { toast("Enter interaction text"); return; }
    try {
        await api("POST", "/leads/" + state.current.id + "/interactions",
            { type: $("#i-type").value, text });
        await openLead(state.current.id);
    } catch (e) { toast(e.message); }
}

async function addTask() {
    const action = $("#t-action").value.trim();
    if (!action) { toast("Enter an action"); return; }
    const dueRaw = $("#t-due").value;
    const dueAt = dueRaw ? new Date(dueRaw).toISOString() : null;
    try {
        await api("POST", "/leads/" + state.current.id + "/tasks", { action, dueAt });
        await openLead(state.current.id);
        await loadMetrics();
    } catch (e) { toast(e.message); }
}

/* ---------- new lead ---------- */

function openNewLead() {
    $("#modal-title").textContent = "New lead";
    $("#modal-body").innerHTML = `
        <div class="section">
            <div class="grid2">
                <label>Name<input id="n-name"></label>
                <label>Stage<select id="n-stage">${STAGES.map(s => `<option>${s}</option>`).join("")}</select></label>
                <label>Email<input id="n-email"></label>
                <label>Phone<input id="n-phone"></label>
                <label>Budget (AED)<input id="n-budget" type="number"></label>
                <label>Bedrooms<input id="n-bedrooms" type="number"></label>
                <label>Emirate<input id="n-emirate"></label>
                <label>Community<input id="n-community"></label>
                <label>Source<input id="n-source"></label>
                <label>Purchase timeframe<input id="n-timeframe"></label>
            </div>
            <label>Stated intent<textarea id="n-intent"></textarea></label>
            <label class="chk"><input type="checkbox" id="n-sample"> Sample / demo lead</label>
            <div class="row" style="margin-top:12px">
                <button class="btn btn-primary" id="btn-create-lead">Create lead</button>
            </div>
            <p class="form-error" id="n-error"></p>
        </div>`;
    $("#modal").hidden = false;
    $("#btn-create-lead").addEventListener("click", createLead);
}

async function createLead() {
    const payload = {
        name: $("#n-name").value.trim(),
        email: nullable($("#n-email").value),
        phone: nullable($("#n-phone").value),
        source: nullable($("#n-source").value),
        stage: $("#n-stage").value,
        budgetAed: numberOrNull($("#n-budget").value),
        bedrooms: intOrNull($("#n-bedrooms").value),
        emirate: nullable($("#n-emirate").value),
        community: nullable($("#n-community").value),
        purchaseTimeframe: nullable($("#n-timeframe").value),
        statedIntent: nullable($("#n-intent").value),
        sample: $("#n-sample").checked,
    };
    if (!payload.name) { $("#n-error").textContent = "Name is required."; return; }
    try {
        const lead = await api("POST", "/leads", payload);
        $("#modal").hidden = true;
        await refresh();
        openLead(lead.id);
    } catch (e) { $("#n-error").textContent = e.message; }
}

/* ---------- import / export ---------- */

function openImport() {
    $("#modal-title").textContent = "Import leads (JSON backup)";
    $("#modal-body").innerHTML = `
        <div class="section">
            <p class="muted">Paste a JSON array of leads or an object with a "leads" array, or choose a file.
            Preview first; committing is idempotent and skips duplicates by email. Your existing data is not erased.</p>
            <input type="file" id="imp-file" accept="application/json">
            <label>JSON<textarea id="imp-text" style="min-height:160px" placeholder='[{"name":"...","email":"..."}]'></textarea></label>
            <div class="row">
                <button class="btn btn-ghost" id="btn-preview">Preview</button>
                <button class="btn btn-primary" id="btn-commit" disabled>Commit import</button>
            </div>
            <div id="imp-result"></div>
            <p class="form-error" id="imp-error"></p>
        </div>`;
    $("#modal").hidden = false;
    $("#imp-file").addEventListener("change", ev => {
        const f = ev.target.files[0];
        if (!f) return;
        const r = new FileReader();
        r.onload = () => { $("#imp-text").value = r.result; };
        r.readAsText(f);
    });
    $("#btn-preview").addEventListener("click", previewImport);
    $("#btn-commit").addEventListener("click", commitImport);
}

function parseImportJson() {
    const raw = $("#imp-text").value.trim();
    if (!raw) throw new Error("Paste or choose a JSON backup first.");
    return JSON.parse(raw);
}

async function previewImport() {
    $("#imp-error").textContent = "";
    try {
        const payload = parseImportJson();
        const r = await api("POST", "/imports/leads/preview", payload);
        $("#imp-result").innerHTML = `
            <div class="section">
                <div class="row" style="gap:18px;flex-wrap:wrap">
                    <span><strong>${r.total}</strong> rows</span>
                    <span><strong>${r.valid}</strong> valid</span>
                    <span><strong>${r.toImport}</strong> to import</span>
                    <span><strong>${r.invalid.length}</strong> invalid</span>
                    <span><strong>${r.duplicatesExisting.length + r.duplicatesInBatch.length}</strong> duplicates</span>
                </div>
                ${r.sampleNames.length ? `<p class="muted">First to import: ${r.sampleNames.map(esc).join(", ")}</p>` : ""}
            </div>`;
        $("#btn-commit").disabled = r.toImport === 0;
    } catch (e) { $("#imp-error").textContent = e.message; }
}

async function commitImport() {
    $("#imp-error").textContent = "";
    try {
        const payload = parseImportJson();
        const key = "imp-" + Date.now() + "-" + Math.random().toString(36).slice(2, 8);
        const res = await fetch("/api/v1/imports/leads/commit", {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": getCookie("XSRF-TOKEN") || "",
                "Idempotency-Key": key,
            },
            body: JSON.stringify(payload),
        });
        if (!res.ok) { const j = await res.json().catch(() => ({})); throw new Error(j.error?.message || "Import failed"); }
        const r = await res.json();
        toast(`Imported ${r.imported}, skipped ${r.skippedDuplicates} duplicates`);
        $("#modal").hidden = true;
        await refresh();
    } catch (e) { $("#imp-error").textContent = e.message; }
}

async function download(path, filename) {
    try {
        const res = await api("GET", path, undefined, { raw: true });
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url; a.download = filename;
        document.body.appendChild(a); a.click(); a.remove();
        URL.revokeObjectURL(url);
    } catch (e) { toast(e.message); }
}

/* ---------- inventory ---------- */

async function openInventory() {
    $("#modal-title").textContent = "Inventory";
    $("#modal-body").innerHTML = `
        <div class="section">
            <h3>Import authorised inventory</h3>
            <p class="muted">Object with a <code>source</code> block and a <code>properties</code> array, or a bare array.
            Imported properties are UNVERIFIED until you verify them. Dedup is by externalId.</p>
            <input type="file" id="inv-file" accept="application/json">
            <label>JSON<textarea id="inv-text" style="min-height:120px" placeholder='{"source":{...},"properties":[...]}'></textarea></label>
            <div class="row">
                <button class="btn btn-ghost" id="btn-inv-preview">Preview</button>
                <button class="btn btn-primary" id="btn-inv-commit" disabled>Commit import</button>
            </div>
            <div id="inv-import-result"></div>
            <p class="form-error" id="inv-error"></p>
        </div>
        <div class="section">
            <h3>Needs attention (stale or unverified)</h3>
            <div id="inv-attention"><p class="muted">Loading…</p></div>
        </div>`;
    $("#modal").hidden = false;
    $("#inv-file").addEventListener("change", ev => {
        const f = ev.target.files[0];
        if (!f) return;
        const r = new FileReader();
        r.onload = () => { $("#inv-text").value = r.result; };
        r.readAsText(f);
    });
    $("#btn-inv-preview").addEventListener("click", previewInventory);
    $("#btn-inv-commit").addEventListener("click", commitInventory);
    loadNeedsAttention();
}

async function loadNeedsAttention() {
    const box = $("#inv-attention");
    try {
        const items = await api("GET", "/properties/needs-attention");
        if (!items.length) { box.innerHTML = `<p class="muted">All authorised inventory is verified and within the freshness window.</p>`; return; }
        box.innerHTML = "";
        items.forEach(d => {
            const p = d.property, fr = d.freshness;
            const row = el("div", "list-item");
            const title = [p.project, p.unitType, bedroomsLabel(p.bedrooms)].filter(Boolean).join(" · ");
            row.innerHTML = `<strong>${esc(title || p.id)}</strong>
                <span class="badge ${fr.status === 'STALE' ? 'badge-sample' : 'badge-archived'}">${esc(fr.status)}</span>
                <span class="muted"> ${esc(money(p.priceAed))} · ${esc(p.emirate || '')}
                ${fr.approvedSource ? '· src: ' + esc(fr.approvedSource) : ''}</span>`;
            const btn = el("button", "btn btn-ghost btn-sm", "Verify");
            btn.style.marginLeft = "8px";
            btn.addEventListener("click", () => verifyProperty(p.id));
            row.appendChild(btn);
            box.appendChild(row);
        });
    } catch (e) { box.innerHTML = `<p class="form-error">${esc(e.message)}</p>`; }
}

async function verifyProperty(id) {
    const src = prompt("Verify against which approved source? (optional)", "Brokerage master sheet");
    try {
        await api("POST", "/properties/" + id + "/verify", {
            approvedSource: src || null, permission: "AUTHORISED",
        });
        toast("Verified");
        loadNeedsAttention();
    } catch (e) { toast(e.message); }
}

function parseInventoryJson() {
    const raw = $("#inv-text").value.trim();
    if (!raw) throw new Error("Paste or choose an inventory JSON first.");
    return JSON.parse(raw);
}

async function previewInventory() {
    $("#inv-error").textContent = "";
    try {
        const r = await api("POST", "/imports/properties/preview", parseInventoryJson());
        $("#inv-import-result").innerHTML = `
            <div class="section">
                <div class="row" style="gap:18px;flex-wrap:wrap">
                    <span><strong>${r.total}</strong> rows</span>
                    <span><strong>${r.toCreate}</strong> new</span>
                    <span><strong>${r.toUpdate}</strong> updates</span>
                    <span><strong>${r.invalid.length}</strong> invalid</span>
                    <span><strong>${r.sampleRows}</strong> samples</span>
                </div>
                ${r.sampleProjects.length ? `<p class="muted">e.g. ${r.sampleProjects.map(esc).join(", ")}</p>` : ""}
            </div>`;
        $("#btn-inv-commit").disabled = (r.toCreate + r.toUpdate) === 0;
    } catch (e) { $("#inv-error").textContent = e.message; }
}

async function commitInventory() {
    $("#inv-error").textContent = "";
    try {
        const payload = parseInventoryJson();
        const key = "inv-" + Date.now() + "-" + Math.random().toString(36).slice(2, 8);
        const res = await fetch("/api/v1/imports/properties/commit", {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": getCookie("XSRF-TOKEN") || "",
                "Idempotency-Key": key,
            },
            body: JSON.stringify(payload),
        });
        if (!res.ok) { const j = await res.json().catch(() => ({})); throw new Error(j.error?.message || "Import failed"); }
        const r = await res.json();
        toast(`Imported: ${r.created} new, ${r.updated} updated`);
        loadNeedsAttention();
    } catch (e) { $("#inv-error").textContent = e.message; }
}

/* ---------- integrations: notifications, team, inbound, outbox ---------- */

async function loadNotifCount() {
    try {
        const r = await api("GET", "/notifications/unread-count");
        const badge = $("#notif-count");
        if (r.unread > 0) { badge.textContent = r.unread; badge.hidden = false; }
        else { badge.hidden = true; }
    } catch (e) { /* ignore */ }
}

async function openNotifications() {
    $("#modal-title").textContent = "Notifications";
    $("#modal-body").innerHTML = `<div class="section">
        <div class="row" style="justify-content:flex-end"><button class="btn btn-ghost btn-sm" id="btn-notif-readall">Mark all read</button></div>
        <div id="notif-list"><p class="muted">Loading…</p></div></div>`;
    $("#modal").hidden = false;
    $("#btn-notif-readall").addEventListener("click", async () => {
        try { await api("POST", "/notifications/read-all"); loadNotifList(); loadNotifCount(); } catch (e) { toast(e.message); }
    });
    loadNotifList();
}

async function loadNotifList() {
    const box = $("#notif-list");
    try {
        const page = await api("GET", "/notifications?size=30");
        if (!page.content.length) { box.innerHTML = `<p class="muted">No notifications.</p>`; return; }
        box.innerHTML = "";
        page.content.forEach(n => {
            const row = el("div", "list-item");
            const unread = !n.readAt;
            row.innerHTML = `<strong>${unread ? "● " : ""}${esc(n.title)}</strong>
                <span class="muted">· ${esc(n.type)} · ${esc(fmt(n.createdAt))}</span><br>${esc(n.body || "")}`;
            if (n.leadId) {
                const open = el("button", "btn btn-ghost btn-sm", "Open lead");
                open.style.marginLeft = "8px";
                open.addEventListener("click", async () => {
                    await api("POST", "/notifications/" + n.id + "/read").catch(() => {});
                    $("#modal").hidden = true; loadNotifCount(); openLead(n.leadId);
                });
                row.appendChild(open);
            } else if (unread) {
                const mark = el("button", "btn btn-ghost btn-sm", "Mark read");
                mark.style.marginLeft = "8px";
                mark.addEventListener("click", async () => { await api("POST", "/notifications/" + n.id + "/read"); loadNotifList(); loadNotifCount(); });
                row.appendChild(mark);
            }
            box.appendChild(row);
        });
    } catch (e) { box.innerHTML = `<p class="form-error">${esc(e.message)}</p>`; }
}

async function openTeam() {
    $("#modal-title").textContent = "Team & inbound sources";
    $("#modal-body").innerHTML = `
        <div class="section">
            <h3>Add member</h3>
            <div class="grid2">
                <label>Email<input id="m-email" type="email"></label>
                <label>Display name<input id="m-name"></label>
                <label>Role<select id="m-role"><option>AGENT</option><option>ADMIN</option><option>OWNER</option></select></label>
                <label>Initial password (min 10)<input id="m-pass" type="password"></label>
            </div>
            <button class="btn btn-primary btn-sm" id="btn-add-member">Add member</button>
            <p class="form-error" id="m-error"></p>
            <div id="member-list"></div>
        </div>
        <div class="section">
            <h3>Inbound lead sources</h3>
            <div class="row"><input id="s-name" placeholder="Source name e.g. Website form" style="flex:1">
                <button class="btn btn-primary btn-sm" id="btn-add-source">Create</button></div>
            <div id="source-token"></div>
            <div id="source-list"></div>
            <p class="form-error" id="s-error"></p>
        </div>`;
    $("#modal").hidden = false;
    $("#btn-add-member").addEventListener("click", addMember);
    $("#btn-add-source").addEventListener("click", addSource);
    loadMembers(); loadSources();
}

async function loadMembers() {
    try {
        const members = await api("GET", "/users");
        state.members = members;
        const box = $("#member-list");
        box.innerHTML = "";
        members.forEach(m => {
            const row = el("div", "list-item");
            row.innerHTML = `<strong>${esc(m.displayName || m.email)}</strong>
                <span class="muted">· ${esc(m.email)} · ${esc(m.role)} · ${esc(m.status)}</span>`;
            box.appendChild(row);
        });
    } catch (e) { /* ignore */ }
}

async function addMember() {
    $("#m-error").textContent = "";
    try {
        await api("POST", "/users", {
            email: $("#m-email").value.trim(), displayName: nullable($("#m-name").value),
            role: $("#m-role").value, initialPassword: $("#m-pass").value,
        });
        toast("Member added"); $("#m-email").value = ""; $("#m-pass").value = ""; loadMembers();
    } catch (e) { $("#m-error").textContent = e.message; }
}

async function loadSources() {
    try {
        const sources = await api("GET", "/inbound-sources");
        const box = $("#source-list");
        box.innerHTML = "";
        sources.forEach(s => {
            const row = el("div", "list-item");
            row.innerHTML = `<strong>${esc(s.name)}</strong> <span class="muted">· ${s.active ? "active" : "inactive"}</span>`;
            if (s.active) {
                const b = el("button", "btn btn-ghost btn-sm", "Deactivate");
                b.style.marginLeft = "8px";
                b.addEventListener("click", async () => { await api("POST", "/inbound-sources/" + s.id + "/deactivate"); loadSources(); });
                row.appendChild(b);
            }
            box.appendChild(row);
        });
    } catch (e) { /* ignore */ }
}

async function addSource() {
    $("#s-error").textContent = "";
    try {
        const r = await api("POST", "/inbound-sources", { name: $("#s-name").value.trim() });
        $("#source-token").innerHTML = `<div class="coach-card"><strong>Token (shown once — copy it now):</strong>
            <code style="word-break:break-all">${esc(r.token)}</code><br>
            <span class="muted">POST it as header <code>X-Inbound-Token</code> to <code>/api/v1/inbound/leads</code>.</span></div>`;
        $("#s-name").value = ""; loadSources();
    } catch (e) { $("#s-error").textContent = e.message; }
}

async function renderOutreach(lead) {
    // Assignment (admins only)
    const assignRow = $("#assign-row");
    if (state.me && (state.me.role === "OWNER" || state.me.role === "ADMIN")) {
        try {
            const members = state.members || await api("GET", "/users");
            state.members = members;
            assignRow.innerHTML = `<label class="muted" style="margin:0">Assign to</label>
                <select id="assign-sel">${members.map(m => `<option value="${m.id}">${esc(m.displayName || m.email)}</option>`).join("")}</select>
                <button class="btn btn-ghost btn-sm" id="btn-assign">Assign</button>`;
            $("#btn-assign").addEventListener("click", async () => {
                try { await api("POST", "/leads/" + lead.id + "/assign", { userId: $("#assign-sel").value }); toast("Assigned"); }
                catch (e) { toast(e.message); }
            });
        } catch (e) { assignRow.innerHTML = ""; }
    }
    // Compose
    $("#outbox-compose").innerHTML = `
        <div class="row" style="gap:8px;flex-wrap:wrap">
            <select id="ob-channel"><option>EMAIL</option><option>WHATSAPP</option><option>SMS</option></select>
            <input id="ob-to" placeholder="Recipient" value="${esc(lead.email || lead.phone || '')}" style="flex:1">
        </div>
        <textarea id="ob-body" placeholder="Message to queue (not sent automatically)" style="min-height:70px;margin-top:6px"></textarea>
        <div class="row" style="gap:8px;margin-top:6px">
            <button class="btn btn-primary btn-sm" id="btn-ob-send">Queue message</button>
            <span class="muted">Delivery is simulated until a real channel is connected.</span>
        </div>`;
    $("#btn-ob-send").addEventListener("click", async () => {
        const body = $("#ob-body").value.trim();
        if (!body) { toast("Enter a message"); return; }
        try {
            await api("POST", "/leads/" + lead.id + "/outbox", {
                channel: $("#ob-channel").value, recipient: $("#ob-to").value.trim(), body,
            });
            $("#ob-body").value = ""; toast("Queued"); loadOutbox(lead.id);
        } catch (e) { toast(e.message); }
    });
    loadOutbox(lead.id);
}

async function loadOutbox(leadId) {
    const box = $("#outbox-list");
    try {
        const page = await api("GET", "/leads/" + leadId + "/outbox?size=20");
        if (!page.content.length) { box.innerHTML = `<p class="muted">No queued messages.</p>`; return; }
        box.innerHTML = "";
        page.content.forEach(m => {
            const row = el("div", "list-item");
            const badge = m.simulated ? `<span class="badge badge-mock">Simulated</span>` : "";
            row.innerHTML = `<strong>${esc(m.channel)}</strong> → ${esc(m.recipient)}
                <span class="badge ${m.status === 'FAILED' ? 'badge-sample' : 'badge-archived'}">${esc(m.status)}</span> ${badge}
                <br><span class="muted">${esc(m.body.slice(0, 80))}${m.body.length > 80 ? "…" : ""}</span>`;
            if (m.status === "PENDING") {
                const c = el("button", "btn btn-ghost btn-sm", "Cancel");
                c.style.marginLeft = "8px";
                c.addEventListener("click", async () => { await api("POST", "/outbox/" + m.id + "/cancel"); loadOutbox(leadId); });
                row.appendChild(c);
            }
            box.appendChild(row);
        });
    } catch (e) { box.innerHTML = `<p class="form-error">${esc(e.message)}</p>`; }
}

/* ---------- command palette (⌘K) ---------- */

let paletteEl = null, palItems = [], palIndex = 0, palTimer;

function toggleTheme() {
    const next = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    try { localStorage.setItem("nest-theme", next); } catch (e) {}
}

function paletteActions() {
    const admin = state.me && (state.me.role === "OWNER" || state.me.role === "ADMIN");
    const a = [
        { label: "New lead", hint: "Create", run: openNewLead, icon: "M12 5v14M5 12h14" },
        { label: "Inventory", hint: "Open", run: openInventory, icon: "M3 7l9-4 9 4-9 4-9-4zM3 7v10l9 4 9-4V7" },
        { label: "Analytics", hint: "View", run: () => showView("analytics"), icon: "M4 20V10M10 20V4M16 20v-7M2 20h20" },
        { label: "Inbox", hint: "View", run: () => showView("inbox"), icon: "M3 13h5l2 3h4l2-3h5M5 5h14l2 8v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4z" },
        { label: "Hot leads", hint: "View", run: () => showView("hot"), icon: "M12 3c1 3-1 4-1 6a3 3 0 0 0 6 0c0-1-.4-2-1-3 2 1 4 4 4 7a7 7 0 0 1-14 0c0-4 3-6 6-10z" },
        { label: "Pipeline", hint: "View", run: () => showView("board"), icon: "M4 6h6v12H4zM14 6h6v7h-6z" },
        { label: "Import leads", hint: "Data", run: openImport, icon: "M12 3v12M8 11l4 4 4-4M4 21h16" },
        { label: "Export CSV", hint: "Data", run: () => download("/exports/leads?format=csv", "leads.csv"), icon: "M12 21V9M8 13l4-4 4 4M4 3h16" },
        { label: "Notifications", hint: "Open", run: openNotifications, icon: "M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" },
        { label: "Toggle theme", hint: "View", run: toggleTheme, icon: "M21 12.8A8.5 8.5 0 1 1 11.2 3a6.6 6.6 0 0 0 9.8 9.8z" },
    ];
    if (admin) a.splice(2, 0, { label: "Team", hint: "Open", run: openTeam, icon: "M9 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 20a6 6 0 0 1 12 0" });
    return a;
}

function openPalette() {
    if (!state.me) return;
    if (!paletteEl) buildPalette();
    paletteEl.hidden = false;
    const input = paletteEl.querySelector("#pal-input");
    input.value = "";
    renderPalette("");
    setTimeout(() => input.focus(), 10);
}
function closePalette() { if (paletteEl) paletteEl.hidden = true; }

function buildPalette() {
    paletteEl = el("div", "palette");
    paletteEl.hidden = true;
    paletteEl.innerHTML = `
        <div class="palette-backdrop" data-pal-close></div>
        <div class="palette-panel">
            <div class="palette-search">
                <svg viewBox="0 0 24 24" class="ic"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4-4"/></svg>
                <input id="pal-input" placeholder="Search leads or run a command…" autocomplete="off" spellcheck="false">
                <span class="kbd">esc</span>
            </div>
            <div id="pal-results" class="palette-results"></div>
        </div>`;
    document.body.appendChild(paletteEl);
    const input = paletteEl.querySelector("#pal-input");
    input.addEventListener("input", () => {
        clearTimeout(palTimer);
        palTimer = setTimeout(() => renderPalette(input.value.trim()), 160);
    });
    input.addEventListener("keydown", palKeydown);
    paletteEl.querySelector("[data-pal-close]").addEventListener("click", closePalette);
}

async function renderPalette(q) {
    const box = paletteEl.querySelector("#pal-results");
    const ql = q.toLowerCase();
    const actions = paletteActions().filter(a => !ql || a.label.toLowerCase().includes(ql));
    let leads = [];
    if (q) {
        try {
            const page = await api("GET", "/leads?size=6&search=" + encodeURIComponent(q));
            leads = page.content;
        } catch (e) { /* ignore */ }
    }
    palItems = [];
    let html = "";
    if (leads.length) {
        html += `<div class="pal-group">Leads</div>`;
        leads.forEach(l => {
            const i = palItems.length; palItems.push({ type: "lead", id: l.id });
            html += `<div class="pal-item" data-i="${i}">
                <span class="pal-av" style="background:${avatarColor(l.name)}">${esc(initials(l.name))}</span>
                <span class="pal-main">${esc(l.name)}<span class="pal-sub">${esc(l.stage)}${l.emirate ? " · " + esc(l.emirate) : ""}</span></span></div>`;
        });
    }
    if (actions.length) {
        html += `<div class="pal-group">Commands</div>`;
        actions.forEach(a => {
            const i = palItems.length; palItems.push({ type: "action", run: a.run });
            html += `<div class="pal-item" data-i="${i}">
                <span class="pal-ic"><svg viewBox="0 0 24 24" class="ic"><path d="${a.icon}"/></svg></span>
                <span class="pal-main">${esc(a.label)}<span class="pal-sub">${esc(a.hint)}</span></span></div>`;
        });
    }
    box.innerHTML = palItems.length ? html : `<div class="pal-empty">No matches</div>`;
    palIndex = 0; highlightPal();
    box.querySelectorAll(".pal-item").forEach(it => {
        it.addEventListener("click", () => activatePal(parseInt(it.dataset.i, 10)));
        it.addEventListener("mousemove", () => { palIndex = parseInt(it.dataset.i, 10); highlightPal(); });
    });
}

function highlightPal() {
    paletteEl.querySelectorAll(".pal-item").forEach(it =>
        it.classList.toggle("is-active", parseInt(it.dataset.i, 10) === palIndex));
    const act = paletteEl.querySelector(".pal-item.is-active");
    if (act) act.scrollIntoView({ block: "nearest" });
}

function palKeydown(e) {
    if (e.key === "ArrowDown") { e.preventDefault(); palIndex = Math.min(palItems.length - 1, palIndex + 1); highlightPal(); }
    else if (e.key === "ArrowUp") { e.preventDefault(); palIndex = Math.max(0, palIndex - 1); highlightPal(); }
    else if (e.key === "Enter") { e.preventDefault(); activatePal(palIndex); }
    else if (e.key === "Escape") { closePalette(); }
}

function activatePal(i) {
    const item = palItems[i];
    if (!item) return;
    closePalette();
    if (item.type === "lead") openLead(item.id);
    else if (item.type === "action") item.run();
}

/* ---------- helpers ---------- */

function nullable(v) { const s = (v || "").trim(); return s === "" ? null : s; }
function numberOrNull(v) { const s = (v || "").trim(); return s === "" ? null : Number(s); }
function intOrNull(v) { const s = (v || "").trim(); return s === "" ? null : parseInt(s, 10); }

/* ---------- events ---------- */

document.addEventListener("click", ev => {
    const el = ev.target.closest && ev.target.closest("[data-close]");
    if (el) {
        const target = document.getElementById(el.getAttribute("data-close"));
        if (target) target.hidden = true;
    }
});

$("#login-form").addEventListener("submit", async ev => {
    ev.preventDefault();
    $("#login-error").textContent = "";
    try {
        await fetch("/api/v1/auth/csrf", { credentials: "same-origin" });
        const me = await api("POST", "/auth/login", {
            email: $("#login-email").value.trim(),
            password: $("#login-password").value,
        });
        onAuthenticated(me);
    } catch (e) { $("#login-error").textContent = e.message; }
});

$("#btn-logout").addEventListener("click", async () => {
    try { await api("POST", "/auth/logout"); } catch (e) { /* ignore */ }
    state.me = null;
    showLogin();
});

$("#tz-select").addEventListener("change", ev => {
    state.tz = ev.target.value;
    if (!$("#drawer").hidden && state.current) openLead(state.current.id);
    loadBoard();
});

let searchTimer;
$("#search").addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(loadBoard, 250);
});
$("#show-samples").addEventListener("change", loadBoard);
$("#show-archived").addEventListener("change", loadBoard);
$("#btn-theme").addEventListener("click", toggleTheme);
const palBtn = $("#btn-palette"); if (palBtn) palBtn.addEventListener("click", openPalette);
$("#nav-pipeline").addEventListener("click", () => showView("board"));
$("#nav-analytics").addEventListener("click", () => showView("analytics"));
const navHot = $("#nav-hot"); if (navHot) navHot.addEventListener("click", () => showView("hot"));
const navInbox = $("#nav-inbox"); if (navInbox) navInbox.addEventListener("click", () => showView("inbox"));
document.addEventListener("keydown", e => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") { e.preventDefault(); openPalette(); }
    else if (e.key === "Escape") {
        if (paletteEl && !paletteEl.hidden) closePalette();
        else if (!$("#drawer").hidden) $("#drawer").hidden = true;
        else if (!$("#modal").hidden) $("#modal").hidden = true;
    }
});
$("#btn-new-lead").addEventListener("click", openNewLead);
$("#btn-import").addEventListener("click", openImport);
$("#btn-inventory").addEventListener("click", openInventory);
$("#btn-team").addEventListener("click", openTeam);
$("#btn-notifs").addEventListener("click", openNotifications);
$("#btn-export-csv").addEventListener("click", () => download("/exports/leads?format=csv", "leads.csv"));
$("#btn-export-json").addEventListener("click", () => download("/exports/leads?format=json", "leads-backup.json"));

init();
