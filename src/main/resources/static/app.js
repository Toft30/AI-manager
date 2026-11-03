/*
 * ODIN-HUD: samtale med manager-agenten, stemme ut via /api/speak (ElevenLabs)
 * og stemme inn via /api/listen. Utan ElevenLabs-nøkkel fell han tilbake til
 * nettlesaren si eiga stemme og talegjenkjenning.
 */

// Live-modus: juster her om han kuttar deg av eller ventar for lenge.
var SILENCE_MS = 950;       // så lenge stille før ytringa er ferdig
var MIN_SPEECH_MS = 350;    // kortare enn dette er eit host, ikkje ei setning
var MAX_SPEECH_MS = 20000;  // hard grense så ein mikrofon som heng ikkje tek opp for alltid
var GREETING = "God dag. Alle system er oppe og går. Kva kan eg hjelpe deg med?";

var $ = function (id) { return document.getElementById(id); };

var state = {
    mode: "idle",          // idle | thinking | speaking | listening
    elevenlabs: false,
    muted: false,
    audioCtx: null,
    playAnalyser: null,
    micAnalyser: null,
    micStream: null,
    level: 0,
    currentAudio: null,
    askAbort: null,
    busy: false
};

// Til feilsøking og verifisering: kva som sist vart lese opp.
window.odin = { lastSpeech: null, state: state };

/* ── Oppstart ─────────────────────────────────────────── */
document.addEventListener("DOMContentLoaded", function () {
    $("bootBtn").addEventListener("click", boot);
    $("askForm").addEventListener("submit", function (e) {
        e.preventDefault();
        var text = $("taskInput").value.trim();
        if (text) { $("taskInput").value = ""; handleInput(text); }
    });
    $("micBtn").addEventListener("click", togglePushToTalk);
    $("liveBtn").addEventListener("click", function () { Live.on ? Live.stop() : Live.start(); });
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") cancelAll();
        if (e.key === "/" && document.activeElement !== $("taskInput")) { e.preventDefault(); $("taskInput").focus(); }
    });

    tickClock(); setInterval(tickClock, 1000);
    fetchStatus(); setInterval(fetchStatus, 5000);
    fetchTasks(); setInterval(fetchTasks, 3000);
    drawReactor();
});

function boot() {
    state.audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    state.playAnalyser = state.audioCtx.createAnalyser();
    state.playAnalyser.fftSize = 512;
    state.playAnalyser.connect(state.audioCtx.destination);
    $("boot").classList.add("gone");
    addLine("odin", GREETING);
    say(GREETING);
}

function setMode(mode) {
    state.mode = mode;
    var chip = $("stateChip");
    chip.className = "state-chip " + mode;
    chip.textContent = { idle: Live.on ? "LIVE" : "KVILER", thinking: "TENKJER", speaking: "TALAR", listening: "LYTTAR" }[mode];
}

/* ── Samtale ──────────────────────────────────────────── */
function handleInput(text) {
    if (text === "/ny") {
        fetch("/api/reset", { method: "POST" });
        $("transcript").innerHTML = "";
        addLine("odin", "Blanke ark. Kva no?");
        return;
    }
    if (text === "/stille") {
        state.muted = !state.muted;
        addLine("odin", state.muted ? "Opplesing er slått av." : "Opplesing er slått på att.");
        return;
    }
    ask(text);
}

function ask(text) {
    if (state.busy) return;
    stopAudio();
    state.busy = true;
    addLine("user", text);
    setMode("thinking");
    $("sendTaskBtn").disabled = true;
    state.askAbort = new AbortController();

    fetch("/api/task?task=" + encodeURIComponent(text), { signal: state.askAbort.signal })
        .then(function (r) { return r.json(); })
        .then(function (task) {
            fetchTasks();
            var reply = task.result || "Eg fekk ikkje noko svar.";
            if (task.status === "FEILA") {
                addLine("error", reply);
                return say("Beklagar, noko gjekk gale. " + reply.replace(/^Feil:\s*/, "").slice(0, 160));
            }
            addLine("odin", reply);
            return say(reply);
        })
        .catch(function (e) {
            if (e.name !== "AbortError") addLine("error", "Klarte ikkje å nå serveren: " + e.message);
        })
        .finally(function () {
            state.busy = false;
            state.askAbort = null;
            $("sendTaskBtn").disabled = false;
            if (state.mode === "thinking") setMode("idle");
        });
}

function addLine(kind, text) {
    var line = document.createElement("div");
    line.className = "line " + kind;
    var who = document.createElement("span");
    who.className = "who";
    who.textContent = { user: "DU", odin: "ODIN", error: "FEIL" }[kind];
    line.appendChild(who);
    line.appendChild(document.createTextNode(text));
    $("transcript").appendChild(line);
    $("transcript").scrollTop = $("transcript").scrollHeight;
}

/* ── Stemme ut ────────────────────────────────────────── */
function say(text) {
    if (state.muted || !text) { setMode("idle"); return Promise.resolve(); }
    stopAudio();
    setMode("speaking");
    var record = { text: text, engine: "elevenlabs", status: null, bytes: 0, duration: 0, startedAt: null, endedAt: null };
    window.odin.lastSpeech = record;

    return fetch("/api/speak", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ text: text })
    }).then(function (r) {
        record.status = r.status;
        if (!r.ok) throw new Error("speak " + r.status);
        return r.blob();
    }).then(function (blob) {
        record.bytes = blob.size;
        return playBlob(blob, record);
    }).catch(function (e) {
        console.warn("ElevenLabs feila, brukar nettlesaren si stemme:", e.message);
        record.engine = "browser";
        return browserSay(text, record);
    }).finally(function () {
        if (state.mode === "speaking") setMode("idle");
    });
}

function playBlob(blob, record) {
    return new Promise(function (resolve, reject) {
        var url = URL.createObjectURL(blob);
        var audio = new Audio(url);
        state.currentAudio = audio;
        if (state.audioCtx) {
            if (state.audioCtx.state === "suspended") state.audioCtx.resume();
            state.audioCtx.createMediaElementSource(audio).connect(state.playAnalyser);
        }
        audio.onplaying = function () { record.startedAt = Date.now(); };
        audio.onloadedmetadata = function () { record.duration = audio.duration; };
        audio.onended = function () { record.endedAt = Date.now(); done(); resolve(); };
        audio.onpause = function () { if (!audio.ended) { done(); resolve(); } };
        audio.onerror = function () { done(); reject(new Error("avspeling feila")); };
        audio.play().catch(function (e) { done(); reject(e); });
        function done() { URL.revokeObjectURL(url); if (state.currentAudio === audio) state.currentAudio = null; }
    });
}

function browserSay(text, record) {
    return new Promise(function (resolve) {
        if (!window.speechSynthesis) return resolve();
        var u = new SpeechSynthesisUtterance(text);
        u.lang = "nb-NO";
        u.onstart = function () { record.startedAt = Date.now(); };
        u.onend = u.onerror = function () { record.endedAt = Date.now(); resolve(); };
        speechSynthesis.speak(u);
    });
}

function stopAudio() {
    if (state.currentAudio) { state.currentAudio.pause(); state.currentAudio = null; }
    if (window.speechSynthesis) speechSynthesis.cancel();
}

function cancelAll() {
    if (state.askAbort) state.askAbort.abort();
    stopAudio();
    if (PushToTalk.rec) PushToTalk.cancel();
    setMode("idle");
}

/* ── Stemme inn ───────────────────────────────────────── */
function ensureMic() {
    if (state.micStream) return Promise.resolve(state.micStream);
    return navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
    }).then(function (stream) {
        state.micStream = stream;
        if (!state.audioCtx) state.audioCtx = new AudioContext();
        state.micAnalyser = state.audioCtx.createAnalyser();
        state.micAnalyser.fftSize = 1024;
        state.audioCtx.createMediaStreamSource(stream).connect(state.micAnalyser);
        return stream;
    });
}

function micLevel() {
    if (!state.micAnalyser) return 0;
    var buf = new Float32Array(state.micAnalyser.fftSize);
    state.micAnalyser.getFloatTimeDomainData(buf);
    var sum = 0;
    for (var i = 0; i < buf.length; i++) sum += buf[i] * buf[i];
    return Math.sqrt(sum / buf.length);
}

function recorderMime() {
    var types = ["audio/webm;codecs=opus", "audio/webm", "audio/ogg;codecs=opus", "audio/mp4"];
    for (var i = 0; i < types.length; i++) if (window.MediaRecorder && MediaRecorder.isTypeSupported(types[i])) return types[i];
    return "";
}

function transcribe(blob) {
    var form = new FormData();
    form.append("audio", blob, "tur.webm");
    return fetch("/api/listen", { method: "POST", body: form })
        .then(function (r) { return r.json().then(function (j) { if (!r.ok) throw new Error(j.error || r.status); return j.text || ""; }); });
}

function heard(text) {
    if (text) ask(text);
    else if (state.mode === "listening") setMode("idle");
}

// Klikk MIC for å byrje, klikk att for å sende.
var PushToTalk = {
    rec: null, chunks: [], cancelled: false,
    start: function () {
        if (!state.elevenlabs) return browserRecognize();
        stopAudio();
        ensureMic().then(function (stream) {
            var mime = recorderMime();
            PushToTalk.chunks = [];
            PushToTalk.cancelled = false;
            PushToTalk.rec = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
            PushToTalk.rec.ondataavailable = function (e) { if (e.data.size) PushToTalk.chunks.push(e.data); };
            PushToTalk.rec.onstop = function () {
                var blob = new Blob(PushToTalk.chunks, { type: PushToTalk.rec.mimeType });
                PushToTalk.rec = null;
                $("micBtn").classList.remove("on");
                if (PushToTalk.cancelled) return;
                setMode("thinking");
                transcribe(blob).then(heard).catch(function (e) { addLine("error", "Lytting feila: " + e.message); setMode("idle"); });
            };
            PushToTalk.rec.start();
            $("micBtn").classList.add("on");
            setMode("listening");
        }).catch(function (e) { addLine("error", "Fekk ikkje tilgang til mikrofonen: " + e.message); });
    },
    stop: function () { if (PushToTalk.rec) PushToTalk.rec.stop(); },
    cancel: function () { PushToTalk.cancelled = true; PushToTalk.stop(); }
};

function togglePushToTalk() {
    if (Live.on) Live.stop();
    PushToTalk.rec ? PushToTalk.stop() : PushToTalk.start();
}

// Reserve når det ikkje finst ElevenLabs-nøkkel: nettlesaren si eiga talegjenkjenning.
function browserRecognize() {
    var SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) return addLine("error", "Denne nettlesaren har ikkje talegjenkjenning. Legg inn ein ElevenLabs-nøkkel.");
    var rec = new SR();
    rec.lang = "nb-NO";
    rec.onresult = function (e) { heard(e.results[0][0].transcript); };
    rec.onerror = function (e) { addLine("error", "Talegjenkjenning feila: " + e.error); setMode("idle"); };
    rec.start();
    setMode("listening");
}

/*
 * Live-modus: handfri samtale. Terskelen blir kalibrert frå støynivået i rommet,
 * og lyttinga er sett på pause medan ODIN tenkjer eller talar, så han ikkje
 * transkriberer seg sjølv.
 */
var Live = {
    on: false, timer: null, rec: null, chunks: [], threshold: 0.02,
    recording: false, voiceStart: 0, lastVoice: 0, resumeAt: 0,

    start: function () {
        if (!state.elevenlabs) return addLine("error", "Live-modus treng ElevenLabs-nøkkel for lytting.");
        ensureMic().then(function () {
            return Live.calibrate();
        }).then(function () {
            Live.on = true;
            $("liveBtn").classList.add("on");
            $("meterThreshold").style.display = "block";
            $("meterThreshold").style.left = Math.min(100, Live.threshold * 400) + "%";
            setMode("idle");
            Live.timer = setInterval(Live.tick, 30);
        }).catch(function (e) { addLine("error", "Fekk ikkje tilgang til mikrofonen: " + e.message); });
    },

    stop: function () {
        Live.on = false;
        clearInterval(Live.timer);
        if (Live.rec && Live.rec.state !== "inactive") { Live.discard = true; Live.rec.stop(); }
        Live.recording = false;
        $("liveBtn").classList.remove("on");
        $("meterThreshold").style.display = "none";
        if (state.mode === "listening" || state.mode === "idle") setMode("idle");
    },

    calibrate: function () {
        return new Promise(function (resolve) {
            var samples = [];
            var t = setInterval(function () { samples.push(micLevel()); }, 30);
            setTimeout(function () {
                clearInterval(t);
                samples.sort(function (a, b) { return a - b; });
                var floor = samples[Math.floor(samples.length * 0.8)] || 0.005;
                Live.threshold = Math.max(floor * 3, 0.015);
                resolve();
            }, 900);
        });
    },

    tick: function () {
        var now = Date.now();
        var suspended = state.busy || state.mode === "speaking" || state.mode === "thinking";
        if (suspended) { Live.resumeAt = now + 400; return; }
        if (now < Live.resumeAt) return;

        var loud = micLevel() > Live.threshold;
        if (!Live.recording) {
            if (loud) Live.begin(now);
            return;
        }
        if (loud) Live.lastVoice = now;
        if (now - Live.voiceStart > MAX_SPEECH_MS) return Live.finish(true);
        if (now - Live.lastVoice > SILENCE_MS) Live.finish(Live.lastVoice - Live.voiceStart > MIN_SPEECH_MS);
    },

    begin: function (now) {
        var mime = recorderMime();
        Live.chunks = [];
        Live.discard = false;
        Live.rec = new MediaRecorder(state.micStream, mime ? { mimeType: mime } : undefined);
        Live.rec.ondataavailable = function (e) { if (e.data.size) Live.chunks.push(e.data); };
        Live.rec.onstop = function () {
            var blob = new Blob(Live.chunks, { type: Live.rec.mimeType });
            if (Live.discard) { if (state.mode === "listening") setMode("idle"); return; }
            setMode("thinking");
            transcribe(blob).then(heard).catch(function (e) { addLine("error", "Lytting feila: " + e.message); setMode("idle"); });
        };
        Live.rec.start();
        Live.recording = true;
        Live.voiceStart = Live.lastVoice = now;
        setMode("listening");
    },

    finish: function (keep) {
        Live.recording = false;
        Live.discard = !keep;
        if (Live.rec && Live.rec.state !== "inactive") Live.rec.stop();
    }
};

/* ── Telemetri og logg ────────────────────────────────── */
function tickClock() {
    $("clock").textContent = new Date().toLocaleTimeString("nn-NO", { hour12: false });
}

function fetchStatus() {
    fetch("/api/status").then(function (r) { return r.json(); }).then(function (s) {
        state.elevenlabs = s.elevenlabs;
        $("tVoice").textContent = s.voice;
        $("tListen").textContent = s.listening;
        $("tTasks").textContent = s.tasks;
        var m = Math.floor(s.uptimeSeconds / 60), h = Math.floor(m / 60);
        $("tUptime").textContent = (h ? h + "t " : "") + (m % 60) + "m " + (s.uptimeSeconds % 60) + "s";
    }).catch(function () { $("tVoice").textContent = "fråkopla"; });
}

function fetchTasks() {
    fetch("/api/tasks").then(function (r) { return r.json(); }).then(renderTasks)
        .catch(function (e) { console.error("Klarte ikkje å hente oppgåver:", e); });
}

function renderTasks(tasks) {
    var list = $("taskList");
    list.innerHTML = "";
    if (tasks.length === 0) {
        list.innerHTML = "<p class='empty'>Ingen handlingar enno.</p>";
        return;
    }
    tasks.sort(function (a, b) { return b.id - a.id; });
    tasks.forEach(function (task) {
        var card = document.createElement("div");
        card.className = "task-card";

        var meta = document.createElement("div");
        meta.className = "task-meta";
        var when = document.createElement("span");
        when.textContent = "#" + task.id + " · " + (task.createdAt || "").replace("T", " ").slice(0, 19);
        meta.appendChild(when);
        meta.appendChild(statusBadge(task.status));
        card.appendChild(meta);

        var title = document.createElement("h3");
        title.textContent = task.description;
        card.appendChild(title);

        if (task.result) {
            var result = document.createElement("p");
            result.className = "task-result";
            result.textContent = task.result;
            card.appendChild(result);
        }
        list.appendChild(card);
    });
}

function statusBadge(status) {
    var badge = document.createElement("span");
    badge.className = "status-badge " + status;
    badge.textContent = status;
    return badge;
}

/* ── Reaktorkjerne ────────────────────────────────────── */
function playLevel() {
    if (!state.playAnalyser || !state.currentAudio) return 0;
    var buf = new Float32Array(state.playAnalyser.fftSize);
    state.playAnalyser.getFloatTimeDomainData(buf);
    var sum = 0;
    for (var i = 0; i < buf.length; i++) sum += buf[i] * buf[i];
    return Math.sqrt(sum / buf.length);
}

function drawReactor() {
    var canvas = $("reactorCanvas"), ctx = canvas.getContext("2d");
    var W = canvas.width, C = W / 2, t0 = performance.now();

    function frame(now) {
        var t = (now - t0) / 1000;
        var mic = micLevel(), play = playLevel();
        var target = Math.min(1, Math.max(mic * 6, play * 5));
        state.level += (target - state.level) * 0.25;
        $("meterFill").style.width = Math.min(100, mic * 400) + "%";

        var color = { idle: "79,216,255", thinking: "255,181,71", speaking: "70,227,154", listening: "255,93,108" }[state.mode];
        var speed = state.mode === "thinking" ? 2.4 : 0.6;
        ctx.clearRect(0, 0, W, W);

        // Glød
        var glow = ctx.createRadialGradient(C, C, 10, C, C, 190);
        glow.addColorStop(0, "rgba(" + color + "," + (0.35 + state.level * 0.5) + ")");
        glow.addColorStop(1, "rgba(" + color + ",0)");
        ctx.fillStyle = glow;
        ctx.fillRect(0, 0, W, W);

        // Roterande ringar med segment
        [[175, 48, 1, 2], [150, 24, -1.4, 3], [125, 12, 2, 1.5]].forEach(function (ring) {
            var r = ring[0], segs = ring[1], dir = ring[2], width = ring[3];
            ctx.strokeStyle = "rgba(" + color + ",0.55)";
            ctx.lineWidth = width;
            for (var i = 0; i < segs; i++) {
                var a = (i / segs) * Math.PI * 2 + t * speed * dir * 0.3;
                ctx.beginPath();
                ctx.arc(C, C, r, a, a + (Math.PI * 2 / segs) * 0.6);
                ctx.stroke();
            }
        });

        // Lydbølgje rundt kjerna
        ctx.beginPath();
        for (var k = 0; k <= 120; k++) {
            var ang = (k / 120) * Math.PI * 2;
            var wob = Math.sin(ang * 8 + t * 6) * state.level * 18 + Math.sin(ang * 3 - t * 2) * 2;
            var rr = 95 + wob;
            var x = C + Math.cos(ang) * rr, y = C + Math.sin(ang) * rr;
            k ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
        }
        ctx.strokeStyle = "rgba(" + color + ",0.9)";
        ctx.lineWidth = 2;
        ctx.stroke();

        // Kjerne
        var core = 55 + state.level * 25 + Math.sin(t * 2) * 2;
        var g = ctx.createRadialGradient(C, C, 0, C, C, core);
        g.addColorStop(0, "rgba(255,255,255,0.95)");
        g.addColorStop(0.4, "rgba(" + color + ",0.85)");
        g.addColorStop(1, "rgba(" + color + ",0.05)");
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.arc(C, C, core, 0, Math.PI * 2);
        ctx.fill();

        requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
}
