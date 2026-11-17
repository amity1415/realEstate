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
    if (!state.notifTimer) state.notifTimer = setInterval(loadNotifCount, 60000);
}

async function refresh() {
    await Promise.all([loadMetrics(), loadBoard()]);
}

async function loadMetrics() {
    try {
        const m = await api("GET", "/metrics");
        const box = $("#metrics");
        box.innerHTML = "";
        const items = [
            ["Active leads", m.activeLeads],
            ["Open tasks", m.openTasks],
            ["Overdue", m.overdueTasks],
            ["Samples", m.sampleLeads],
        ];
        items.forEach(([lbl, num]) => {
            const card = el("div", "metric");
            card.appendChild(el("div", "num", num));
            card.appendChild(el("div", "lbl", lbl));
            box.appendChild(card);
        });
    } catch (e) { /* non-fatal */ }
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

    const page = await api("GET", "/leads?" + params.toString());
    const byStage = {};
    STAGES.forEach(s => byStage[s] = []);
    page.content.forEach(l => { (byStage[l.stage] || (byStage[l.stage] = [])).push(l); });

    const board = $("#board");
    board.innerHTML = "";
    STAGES.forEach(stage => {
        const col = el("div", "column");
        const head = el("div", "column-head");
        head.appendChild(el("span", "title", stage));
        head.appendChild(el("span", "count", String(byStage[stage].length)));
        col.appendChild(head);
        byStage[stage].forEach(l => col.appendChild(leadCard(l)));
        board.appendChild(col);
    });
}

function leadCard(l) {
    const card = el("div", "card");
    card.appendChild(el("div", "name", l.name));
    const meta = el("div", "meta");
    if (l.budgetAed != null) meta.appendChild(el("span", null, money(l.budgetAed)));
    if (l.bedrooms != null) meta.appendChild(el("span", null, bedroomsLabel(l.bedrooms)));
    if (l.emirate) meta.appendChild(el("span", null, l.emirate));
    card.appendChild(meta);
    const badges = el("div", "meta badges");
    if (l.sample) badges.appendChild(el("span", "badge badge-sample", "Sample"));
    if (l.archivedAt) badges.appendChild(el("span", "badge badge-archived", "Archived"));
    if (badges.children.length) card.appendChild(badges);
    card.addEventListener("click", () => openLead(l.id));
    return card;
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

/* ---------- helpers ---------- */

function nullable(v) { const s = (v || "").trim(); return s === "" ? null : s; }
function numberOrNull(v) { const s = (v || "").trim(); return s === "" ? null : Number(s); }
function intOrNull(v) { const s = (v || "").trim(); return s === "" ? null : parseInt(s, 10); }

/* ---------- events ---------- */

document.addEventListener("click", ev => {
    const close = ev.target.getAttribute && ev.target.getAttribute("data-close");
    if (close) document.getElementById(close).hidden = true;
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
$("#btn-new-lead").addEventListener("click", openNewLead);
$("#btn-import").addEventListener("click", openImport);
$("#btn-inventory").addEventListener("click", openInventory);
$("#btn-team").addEventListener("click", openTeam);
$("#btn-notifs").addEventListener("click", openNotifications);
$("#btn-export-csv").addEventListener("click", () => download("/exports/leads?format=csv", "leads.csv"));
$("#btn-export-json").addEventListener("click", () => download("/exports/leads?format=json", "leads-backup.json"));

init();
