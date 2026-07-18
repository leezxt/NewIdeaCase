(() => {
    "use strict";

    const COLORS = {
        foreground: "#e8f1f5",
        muted: "#86a0ac",
        border: "#203946",
        cyan: "#36c6e3",
        green: "#52d58a",
        amber: "#f3bd55",
        red: "#f06969",
        blue: "#6f9df5",
        violet: "#ad8df3",
        surface: "#0c1720"
    };
    const REFRESH_INTERVAL_MS = 5000;
    const MAX_SAMPLES = 20;
    const charts = new Map();
    const history = [];
    const events = [];
    let previousRequestCount = null;
    let previousSampleTime = null;
    let lastHealth = null;

    const byId = (id) => document.getElementById(id);
    const formatInteger = new Intl.NumberFormat("zh-TW", { maximumFractionDigits: 0 });
    const formatDecimal = new Intl.NumberFormat("zh-TW", { minimumFractionDigits: 1, maximumFractionDigits: 1 });

    function parseLabels(source) {
        const labels = {};
        const expression = /([a-zA-Z_][a-zA-Z0-9_]*)="((?:\\.|[^"])*)"/g;
        let match;
        while ((match = expression.exec(source)) !== null) {
            labels[match[1]] = match[2].replace(/\\n/g, "\n").replace(/\\"/g, "\"");
        }
        return labels;
    }

    function parsePrometheus(text) {
        const samples = [];
        for (const rawLine of text.split("\n")) {
            const line = rawLine.trim();
            if (!line || line.startsWith("#")) {
                continue;
            }
            const match = line.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{([^}]*)\})?\s+(-?(?:\d+(?:\.\d+)?|\.\d+)(?:[eE][+-]?\d+)?|NaN|[+-]Inf)/);
            if (!match) {
                continue;
            }
            const value = Number(match[3]);
            if (Number.isFinite(value)) {
                samples.push({ name: match[1], labels: parseLabels(match[2] || ""), value });
            }
        }
        return samples;
    }

    function metricSamples(samples, name, filter = {}) {
        return samples.filter((sample) => sample.name === name
            && Object.entries(filter).every(([key, value]) => sample.labels[key] === value));
    }

    function metricSum(samples, name, filter = {}) {
        return metricSamples(samples, name, filter).reduce((sum, sample) => sum + sample.value, 0);
    }

    function metricFirst(samples, name, filter = {}, fallback = 0) {
        return metricSamples(samples, name, filter)[0]?.value ?? fallback;
    }

    function percent(value) {
        return Math.max(0, Math.min(100, Number.isFinite(value) ? value : 0));
    }

    function formatTime(date = new Date()) {
        return date.toLocaleTimeString("zh-TW", { hour12: false });
    }

    function updateClock() {
        const now = new Date();
        byId("current-time").textContent = formatTime(now);
        byId("current-date").textContent = now.toLocaleDateString("zh-TW", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            weekday: "short"
        });
    }

    function addEvent(message, level = "info") {
        events.unshift({ time: formatTime(), message, level });
        if (events.length > 5) {
            events.length = 5;
        }
        const list = byId("event-list");
        list.replaceChildren(...events.map((event) => {
            const item = document.createElement("li");
            const time = document.createElement("time");
            const messageNode = document.createElement("span");
            const badge = document.createElement("b");
            time.textContent = event.time;
            messageNode.textContent = event.message;
            badge.textContent = event.level.toUpperCase();
            badge.dataset.level = event.level;
            item.append(time, messageNode, badge);
            return item;
        }));
    }

    function chart(id) {
        if (!charts.has(id)) {
            charts.set(id, echarts.init(byId(id), null, { renderer: "canvas" }));
        }
        return charts.get(id);
    }

    function baseOption() {
        return {
            animation: !window.matchMedia("(prefers-reduced-motion: reduce)").matches,
            animationDuration: 850,
            animationDurationUpdate: 700,
            animationEasing: "cubicOut",
            animationEasingUpdate: "cubicInOut",
            textStyle: { color: COLORS.foreground, fontFamily: "Segoe UI, Microsoft JhengHei, sans-serif" },
            tooltip: {
                trigger: "axis",
                backgroundColor: "#101e29",
                borderColor: COLORS.border,
                textStyle: { color: COLORS.foreground }
            }
        };
    }

    function axisStyle() {
        return {
            axisLine: { lineStyle: { color: COLORS.border } },
            axisTick: { show: false },
            axisLabel: { color: COLORS.muted, fontSize: 11 },
            splitLine: { lineStyle: { color: COLORS.border, opacity: 0.55 } }
        };
    }

    function updateSummary(snapshot) {
        const healthUp = snapshot.health === "UP";
        byId("health-value").textContent = healthUp ? "正常" : "異常";
        byId("health-value").dataset.state = healthUp ? "up" : "down";
        byId("health-meta").textContent = healthUp ? "Readiness 已通過" : "健康檢查未通過";
        byId("request-value").textContent = formatInteger.format(snapshot.requests);
        byId("rps-value").textContent = formatDecimal.format(snapshot.rps);
        byId("success-value").textContent = snapshot.requests > 0
            ? `${formatDecimal.format(snapshot.successRate)}%`
            : "--";
        byId("error-meta").textContent = `${formatInteger.format(snapshot.errors)} 次 4xx / 5xx`;
        byId("latency-value").textContent = snapshot.requests > 0
            ? `${formatDecimal.format(snapshot.avgLatency)} ms`
            : "--";
        byId("live-state").dataset.state = healthUp ? "online" : "error";
        byId("live-label").textContent = healthUp ? "即時" : "異常";
    }

    function updateTrafficChart(snapshot) {
        history.push({ time: formatTime(snapshot.sampledAt), rps: snapshot.rps, latency: snapshot.avgLatency });
        if (history.length > MAX_SAMPLES) {
            history.shift();
        }
        chart("traffic-chart").setOption({
            ...baseOption(),
            legend: {
                right: 0,
                top: 4,
                textStyle: { color: COLORS.muted, fontSize: 11 },
                itemWidth: 14,
                data: ["請求速率", "平均延遲"]
            },
            grid: { left: 48, right: 50, top: 42, bottom: 34 },
            xAxis: { type: "category", boundaryGap: false, data: history.map((point) => point.time), ...axisStyle() },
            yAxis: [
                { type: "value", name: "req/s", nameTextStyle: { color: COLORS.muted }, ...axisStyle() },
                { ...axisStyle(), type: "value", name: "ms", nameTextStyle: { color: COLORS.muted }, splitLine: { show: false } }
            ],
            series: [
                {
                    name: "請求速率",
                    type: "line",
                    smooth: true,
                    showSymbol: history.length < 4,
                    symbolSize: 7,
                    data: history.map((point) => point.rps),
                    lineStyle: { width: 2, color: COLORS.cyan },
                    itemStyle: { color: COLORS.cyan },
                    areaStyle: { color: "rgba(54,198,227,0.12)" }
                },
                {
                    name: "平均延遲",
                    type: "line",
                    yAxisIndex: 1,
                    smooth: true,
                    showSymbol: false,
                    data: history.map((point) => point.latency),
                    lineStyle: { width: 2, color: COLORS.amber },
                    itemStyle: { color: COLORS.amber }
                }
            ]
        }, true);
    }

    function gaugeData(name, value, color, center) {
        return {
            name,
            type: "gauge",
            center,
            radius: "62%",
            min: 0,
            max: 100,
            startAngle: 205,
            endAngle: -25,
            splitNumber: 4,
            progress: { show: true, width: 9, itemStyle: { color } },
            axisLine: { lineStyle: { width: 9, color: [[1, COLORS.border]] } },
            axisTick: { show: false },
            splitLine: { distance: -13, length: 4, lineStyle: { color: COLORS.muted, width: 1 } },
            axisLabel: { show: false },
            pointer: { width: 3, length: "48%", itemStyle: { color } },
            anchor: { show: true, size: 7, itemStyle: { color } },
            title: { offsetCenter: [0, "68%"], color: COLORS.muted, fontSize: 11 },
            detail: {
                valueAnimation: true,
                offsetCenter: [0, "30%"],
                formatter: "{value}%",
                color: COLORS.foreground,
                fontSize: 18,
                fontWeight: 500
            },
            data: [{ name, value: Number(value.toFixed(1)) }]
        };
    }

    function updateResourceChart(snapshot) {
        chart("resource-chart").setOption({
            ...baseOption(),
            tooltip: { show: false },
            series: [
                gaugeData("Process CPU", snapshot.cpu, COLORS.green, ["28%", "53%"]),
                gaugeData("JVM Heap", snapshot.heap, COLORS.blue, ["72%", "53%"])
            ]
        }, true);
    }

    function updateStatusChart(snapshot) {
        const data = Object.entries(snapshot.statuses)
            .filter(([, value]) => value > 0)
            .map(([name, value]) => ({ name, value }));
        chart("status-chart").setOption({
            ...baseOption(),
            tooltip: { trigger: "item", formatter: "{b}<br>{c} 次 · {d}%" },
            legend: { bottom: 0, textStyle: { color: COLORS.muted, fontSize: 11 }, itemWidth: 10 },
            color: [COLORS.green, COLORS.blue, COLORS.amber, COLORS.red],
            series: [{
                type: "pie",
                radius: ["47%", "70%"],
                center: ["50%", "46%"],
                minAngle: 4,
                label: { color: COLORS.foreground, formatter: "{b}\n{c}" },
                labelLine: { lineStyle: { color: COLORS.border } },
                itemStyle: { borderColor: COLORS.surface, borderWidth: 3 },
                data: data.length ? data : [{ name: "尚無請求", value: 1, itemStyle: { color: COLORS.border } }]
            }]
        }, true);
    }

    function updateIngestionChart(snapshot) {
        const categories = ["Pending", "Processing", "Failed", "Completed", "Retried"];
        const values = [
            snapshot.ingestion.pending,
            snapshot.ingestion.processing,
            snapshot.ingestion.failed,
            snapshot.ingestion.completed,
            snapshot.ingestion.retried
        ];
        chart("ingestion-chart").setOption({
            ...baseOption(),
            tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
            grid: { left: 42, right: 12, top: 22, bottom: 44 },
            xAxis: { ...axisStyle(), type: "category", data: categories, axisLabel: { color: COLORS.muted, fontSize: 10, rotate: 22 } },
            yAxis: { type: "value", minInterval: 1, ...axisStyle() },
            series: [{
                type: "bar",
                barMaxWidth: 28,
                data: values.map((value, index) => ({
                    value,
                    itemStyle: { color: [COLORS.amber, COLORS.blue, COLORS.red, COLORS.green, COLORS.violet][index] }
                })),
                label: { show: true, position: "top", color: COLORS.foreground }
            }]
        }, true);
    }

    function updateEndpointChart(snapshot) {
        const endpoints = snapshot.endpoints.slice(0, 6).reverse();
        chart("endpoint-chart").setOption({
            ...baseOption(),
            tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, valueFormatter: (value) => `${formatDecimal.format(value)} ms` },
            grid: { left: 118, right: 42, top: 20, bottom: 28 },
            xAxis: { type: "value", name: "ms", nameTextStyle: { color: COLORS.muted }, ...axisStyle() },
            yAxis: {
                ...axisStyle(),
                type: "category",
                data: endpoints.map((item) => item.uri),
                axisLabel: { color: COLORS.muted, fontSize: 10, width: 106, overflow: "truncate" }
            },
            series: [{
                type: "bar",
                barMaxWidth: 16,
                data: endpoints.map((item) => Number(item.latency.toFixed(2))),
                itemStyle: { color: COLORS.cyan, borderRadius: [0, 3, 3, 0] },
                label: { show: true, position: "right", color: COLORS.foreground, formatter: "{c}" }
            }]
        }, true);
    }

    function updateThreadChart(snapshot) {
        const data = [
            { name: "Live", value: snapshot.threads.live, itemStyle: { color: COLORS.cyan } },
            { name: "Daemon", value: snapshot.threads.daemon, itemStyle: { color: COLORS.violet } },
            { name: "Peak", value: snapshot.threads.peak, itemStyle: { color: COLORS.amber } }
        ];
        chart("thread-chart").setOption({
            ...baseOption(),
            tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
            grid: { left: 70, right: 24, top: 20, bottom: 28 },
            xAxis: { type: "value", minInterval: 1, ...axisStyle() },
            yAxis: { type: "category", data: data.map((item) => item.name), ...axisStyle() },
            series: [{
                type: "bar",
                barWidth: 18,
                data,
                label: { show: true, position: "right", color: COLORS.foreground }
            }]
        }, true);
    }

    function buildSnapshot(health, samples) {
        const now = new Date();
        const requests = metricSum(samples, "http_server_requests_seconds_count");
        const duration = metricSum(samples, "http_server_requests_seconds_sum");
        const elapsedSeconds = previousSampleTime ? Math.max(1, (now.getTime() - previousSampleTime) / 1000) : 1;
        const rps = previousRequestCount === null ? 0 : Math.max(0, (requests - previousRequestCount) / elapsedSeconds);
        previousRequestCount = requests;
        previousSampleTime = now.getTime();

        const statuses = { "2xx": 0, "3xx": 0, "4xx": 0, "5xx": 0 };
        for (const sample of metricSamples(samples, "http_server_requests_seconds_count")) {
            const code = sample.labels.status || "";
            const bucket = `${code.charAt(0)}xx`;
            if (Object.hasOwn(statuses, bucket)) {
                statuses[bucket] += sample.value;
            }
        }
        const errors = statuses["4xx"] + statuses["5xx"];
        const successRate = requests > 0 ? ((requests - errors) / requests) * 100 : 0;
        const endpointsByUri = new Map();
        for (const sample of metricSamples(samples, "http_server_requests_seconds_count")) {
            const uri = sample.labels.uri || "unknown";
            if (uri === "UNKNOWN" || uri === "root") {
                continue;
            }
            const current = endpointsByUri.get(uri) || { uri, count: 0, duration: 0 };
            current.count += sample.value;
            endpointsByUri.set(uri, current);
        }
        for (const sample of metricSamples(samples, "http_server_requests_seconds_sum")) {
            const current = endpointsByUri.get(sample.labels.uri || "unknown");
            if (current) {
                current.duration += sample.value;
            }
        }
        const heapUsed = metricSum(samples, "jvm_memory_used_bytes", { area: "heap" });
        let heapLimit = metricSum(samples, "jvm_memory_max_bytes", { area: "heap" });
        if (heapLimit <= 0) {
            heapLimit = metricSum(samples, "jvm_memory_committed_bytes", { area: "heap" });
        }
        return {
            health,
            sampledAt: now,
            requests,
            rps,
            errors,
            statuses,
            successRate,
            avgLatency: requests > 0 ? (duration / requests) * 1000 : 0,
            cpu: percent(metricFirst(samples, "process_cpu_usage") * 100),
            heap: percent(heapLimit > 0 ? (heapUsed / heapLimit) * 100 : 0),
            ingestion: {
                pending: metricFirst(samples, "knowledge_ingestion_queue_size", { status: "pending" }),
                processing: metricFirst(samples, "knowledge_ingestion_queue_size", { status: "processing" }),
                failed: metricFirst(samples, "knowledge_ingestion_queue_size", { status: "failed" }),
                completed: metricFirst(samples, "knowledge_ingestion_tasks_total", { outcome: "completed" }),
                retried: metricFirst(samples, "knowledge_ingestion_tasks_total", { outcome: "retried" })
            },
            endpoints: Array.from(endpointsByUri.values())
                .filter((item) => item.count > 0)
                .map((item) => ({ uri: item.uri, latency: (item.duration / item.count) * 1000 }))
                .sort((left, right) => right.latency - left.latency),
            threads: {
                live: metricFirst(samples, "jvm_threads_live_threads"),
                daemon: metricFirst(samples, "jvm_threads_daemon_threads"),
                peak: metricFirst(samples, "jvm_threads_peak_threads")
            }
        };
    }

    function renderSnapshot(snapshot) {
        updateSummary(snapshot);
        updateTrafficChart(snapshot);
        updateResourceChart(snapshot);
        updateStatusChart(snapshot);
        updateIngestionChart(snapshot);
        updateEndpointChart(snapshot);
        updateThreadChart(snapshot);
        byId("refresh-label").textContent = `更新 ${formatTime(snapshot.sampledAt)}`;
        byId("sample-count").textContent = `${history.length} samples`;
        if (lastHealth !== snapshot.health) {
            addEvent(`應用健康狀態：${snapshot.health}`, snapshot.health === "UP" ? "ok" : "error");
            lastHealth = snapshot.health;
        } else {
            addEvent(`指標更新完成 · ${formatInteger.format(snapshot.requests)} requests`, "info");
        }
    }

    async function refresh() {
        try {
            const [healthResponse, metricsResponse] = await Promise.all([
                fetch("/actuator/health", { cache: "no-store" }),
                fetch("/actuator/prometheus", { cache: "no-store" })
            ]);
            if (!healthResponse.ok || !metricsResponse.ok) {
                throw new Error(`HTTP ${healthResponse.status}/${metricsResponse.status}`);
            }
            const [healthPayload, metricsText] = await Promise.all([
                healthResponse.json(),
                metricsResponse.text()
            ]);
            renderSnapshot(buildSnapshot(healthPayload.status || "UNKNOWN", parsePrometheus(metricsText)));
        } catch (error) {
            byId("live-state").dataset.state = "error";
            byId("live-label").textContent = "離線";
            byId("health-value").textContent = "無法連線";
            byId("health-value").dataset.state = "down";
            byId("health-meta").textContent = "Actuator 指標無回應";
            addEvent(`監控更新失敗：${error.message}`, "error");
        }
    }

    function initialize() {
        if (typeof echarts === "undefined") {
            byId("live-state").dataset.state = "error";
            byId("live-label").textContent = "圖表載入失敗";
            addEvent("ECharts 資源載入失敗", "error");
            return;
        }
        updateClock();
        setInterval(updateClock, 1000);
        addEvent("監控頁面已就緒", "ok");
        refresh();
        setInterval(refresh, REFRESH_INTERVAL_MS);
        const resizeObserver = new ResizeObserver(() => charts.forEach((instance) => instance.resize()));
        resizeObserver.observe(document.querySelector(".dashboard-shell"));
    }

    document.addEventListener("DOMContentLoaded", initialize);
})();
