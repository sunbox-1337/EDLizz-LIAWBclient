package net.ccbluex.liquidbounce.web

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.ccbluex.liquidbounce.LiquidBounce.moduleManager
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.config.*
import java.awt.Color
import java.io.*
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpExchange
import net.ccbluex.liquidbounce.features.module.Category

/**
 * Web模块数据结构
 */
data class WebModule(
    val name: String,
    val category: String,
    val description: String,
    val state: Boolean,
    val values: List<WebValue>
)

/**
 * Web参数值数据结构
 */
data class WebValue(
    val name: String,
    val type: String,
    val value: String,
    val min: Double? = null,
    val max: Double? = null,
    val choices: List<String>? = null
)

/**
 * 模块更新请求数据结构
 */
data class ModuleUpdateRequest(
    val moduleName: String,
    val state: Boolean? = null,
    val valueName: String? = null,
    val value: String? = null
)

/**
 * ClickGUI Web界面管理器
 */
object ClickGuiWebInterface {
    private const val PORT = 8081
    private var server: HttpServer? = null
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var lastModuleHash: String = "" // 用于检测模块状态变化

    /**
     * 启动Web服务器
     */
    fun start() {
        try {
            server = HttpServer.create(InetSocketAddress(PORT), 0)

            // 根路径 - 提供HTML页面
            server?.createContext("/", RootHandler())

            // API端点 - 获取模块列表
            server?.createContext("/api/modules", ModulesHandler())

            // API端点 - 更新模块状态和参数
            server?.createContext("/api/update", UpdateHandler())

            // API端点 - 检查模块状态变化
            server?.createContext("/api/check-updates", CheckUpdatesHandler())

            server?.start()
            println("ClickGUI Web界面已启动: http://localhost:$PORT")
        } catch (e: Exception) {
            println("启动Web服务器失败: ${e.message}")
        }
    }

    /**
     * 停止Web服务器
     */
    fun stop() {
        server?.stop(0)
        println("ClickGUI Web界面已停止")
    }

    /**
     * 根路径处理器 - 提供HTML页面
     */
    private class RootHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                if (exchange.requestMethod == "GET") {
                    val response = generateClickGuiPage().toByteArray()
                    exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
                    exchange.sendResponseHeaders(200, response.size.toLong())
                    exchange.responseBody.use { os -> os.write(response) }
                } else {
                    exchange.sendResponseHeaders(405, -1) // 方法不允许
                }
            } catch (e: Exception) {
                exchange.sendResponseHeaders(500, -1) // 内部服务器错误
            }
        }
    }

    /**
     * 模块列表处理器 - 提供JSON格式的模块数据
     */
    private class ModulesHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                if (exchange.requestMethod == "GET") {
                    // 修改这里：使用moduleManager本身作为集合来获取模块列表
                    val modules = moduleManager.map { module ->
                        WebModule(
                            name = module.name,
                            category = module.category.displayName,
                            description = module.description,
                            state = module.state,
                            values = module.values.filter { it.shouldRender() }.map { value ->
                                WebValue(
                                    name = value.name,
                                    type = value::class.simpleName ?: "Unknown",
                                    value = value.get().toString(),
                                    min = when (value) {
                                        is FloatValue -> value.range.start.toDouble()
                                        is IntValue -> value.range.first.toDouble()
                                        is FloatRangeValue -> value.range.start.toDouble()
                                        is IntRangeValue -> value.range.first.toDouble()
                                        else -> null
                                    },
                                    max = when (value) {
                                        is FloatValue -> value.range.endInclusive.toDouble()
                                        is IntValue -> value.range.last.toDouble()
                                        is FloatRangeValue -> value.range.endInclusive.toDouble()
                                        is IntRangeValue -> value.range.last.toDouble()
                                        else -> null
                                    },
                                    choices = when (value) {
                                        is ListValue -> value.values.toList()
                                        else -> null
                                    }
                                )
                            }
                        )
                    }

                    val response = gson.toJson(modules).toByteArray()
                    exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
                    exchange.sendResponseHeaders(200, response.size.toLong())
                    exchange.responseBody.use { os -> os.write(response) }
                } else {
                    exchange.sendResponseHeaders(405, -1) // 方法不允许
                }
            } catch (e: Exception) {
                exchange.sendResponseHeaders(500, -1) // 内部服务器错误
            }
        }
    }

    /**
     * 更新处理器 - 处理模块状态和参数更新
     */
    private class UpdateHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                if (exchange.requestMethod == "POST") {
                    val requestBody = exchange.requestBody.bufferedReader().readText()
                    val request = gson.fromJson(requestBody, ModuleUpdateRequest::class.java)

                    // 修改这里：使用moduleManager本身来查找模块
                    val module = moduleManager.find {
                        it.name.equals(request.moduleName, ignoreCase = true)
                    }

                    if (module != null) {
                        // 更新模块状态
                        request.state?.let { state ->
                            if (state != module.state) {
                                module.state = state
                                if (state) {
                                    module.onEnable()
                                } else {
                                    module.onDisable()
                                }
                            }
                        }

                        // 更新模块参数
                        if (request.valueName != null && request.value != null) {
                            val value = module.values.find {
                                it.name.equals(request.valueName, ignoreCase = true)
                            }

                            when (value) {
                                is BoolValue -> value.set(request.value.toBoolean())
                                is IntValue -> request.value.toIntOrNull()?.let { value.set(it) }
                                is FloatValue -> request.value.toFloatOrNull()?.let { value.set(it) }
                                is TextValue -> value.set(request.value)
                                is ListValue -> value.set(request.value)
                                is ColorValue -> {
                                    try {
                                        val colorInt = request.value.toIntOrNull() ?: request.value.toLongOrNull()?.let { it.toInt() }
                                        colorInt?.let { value.set(Color(it, true)) }
                                    } catch (e: Exception) {
                                        // 处理颜色解析错误
                                    }
                                }
                                is IntRangeValue -> {
                                    try {
                                        val rangeParts = request.value.split("..")
                                        if (rangeParts.size == 2) {
                                            val first = rangeParts[0].toIntOrNull()
                                            val last = rangeParts[1].toIntOrNull()
                                            if (first != null && last != null) {
                                                value.set(first..last)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 处理范围解析错误
                                    }
                                }
                                is FloatRangeValue -> {
                                    try {
                                        val rangeParts = request.value.split("..")
                                        if (rangeParts.size == 2) {
                                            val first = rangeParts[0].toFloatOrNull()
                                            val last = rangeParts[1].toFloatOrNull()
                                            if (first != null && last != null) {
                                                value.set(first..last)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 处理范围解析错误
                                    }
                                }
                                else -> {} // 忽略不支持的参数类型
                            }
                        }

                        exchange.sendResponseHeaders(200, -1) // 成功
                    } else {
                        exchange.sendResponseHeaders(404, -1) // 模块未找到
                    }
                } else {
                    exchange.sendResponseHeaders(405, -1) // 方法不允许
                }
            } catch (e: Exception) {
                exchange.sendResponseHeaders(400, -1) // 错误请求
            }
        }
    }

    private class CheckUpdatesHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                if (exchange.requestMethod == "GET") {
                    // 生成当前模块状态的哈希值
                    val currentHash = generateModuleHash()
                    val hasChanges = currentHash != ClickGuiWebInterface.lastModuleHash

                    // 如果有变化，更新最后哈希值
                    if (hasChanges) {
                        ClickGuiWebInterface.lastModuleHash = currentHash
                    }

                    val response = gson.toJson(
                        mapOf(
                            "hasChanges" to hasChanges,
                            "timestamp" to System.currentTimeMillis()
                        )
                    ).toByteArray()

                    exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
                    exchange.sendResponseHeaders(200, response.size.toLong())
                    exchange.responseBody.use { os -> os.write(response) }
                } else {
                    exchange.sendResponseHeaders(405, -1) // 方法不允许
                }
            } catch (e: Exception) {
                exchange.sendResponseHeaders(500, -1) // 内部服务器错误
            }
        }
    }
    private fun generateModuleHash(): String {
        val moduleStates = moduleManager.map { module ->
            "${module.name}:${module.state}:${module.values.joinToString("|") {
                "${it.name}:${it.get()}"
            }}"
        }.sorted().joinToString("#")

        return Integer.toHexString(moduleStates.hashCode())
    }
    /**
     * 生成ClickGUI HTML页面
     */
    private fun generateClickGuiPage(): String {
        return """
    <!DOCTYPE html>
    <html lang="zh-CN">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Lizz ClickGUI</title>
        <style>
            :root {
                --primary-bg: #1a1a1a;
                --secondary-bg: #2d2d2d;
                --accent: #0096ff;
                --accent-hover: #0077cc;
                --text-primary: #ffffff;
                --text-secondary: #cccccc;
                --border: #404040;
                --success: #4caf50;
                --error: #f44336;
                --radius: 8px;
                --radius-sm: 4px;
            }

            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }

            body {
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                background: var(--primary-bg);
                color: var(--text-primary);
                height: 100vh;
                overflow: hidden;
            }

            .header {
                background: var(--secondary-bg);
                padding: 15px 20px;
                border-bottom: 1px solid var(--border);
                font-size: 18px;
                font-weight: bold;
            }

            .main-container {
                display: flex;
                height: calc(100vh - 60px);
            }

            /* 侧边栏样式 */
            .sidebar {
                width: 200px;
                background: var(--secondary-bg);
                border-right: 1px solid var(--border);
                padding: 15px 0;
                overflow-y: auto;
            }

            .category-item {
                padding: 12px 20px;
                cursor: pointer;
                transition: all 0.2s ease;
                border-radius: var(--radius-sm);
                margin: 2px 10px;
                font-size: 14px;
            }

            .category-item:hover {
                background: rgba(255, 255, 255, 0.1);
            }

            .category-item.active {
                background: var(--accent);
                color: white;
            }

            /* 主内容区样式 */
            .content-area {
                flex: 1;
                display: flex;
                flex-direction: column;
                overflow: hidden;
            }

            .search-box {
                padding: 15px 20px;
                border-bottom: 1px solid var(--border);
            }

            .search-input {
                width: 100%;
                padding: 10px 15px;
                background: var(--secondary-bg);
                border: 1px solid var(--border);
                color: var(--text-primary);
                border-radius: var(--radius);
                font-size: 14px;
            }

            .search-input:focus {
                outline: none;
                border-color: var(--accent);
            }

            .modules-container {
                flex: 1;
                padding: 20px;
                overflow-y: auto;
            }

            .module-grid {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
                gap: 15px;
            }

            /* 模块卡片样式 */
            .module-card {
                background: var(--secondary-bg);
                border: 1px solid var(--border);
                border-radius: var(--radius);
                padding: 15px;
                transition: all 0.2s ease;
                position: relative;
                height: auto;
            }

            .module-card:hover {
                border-color: var(--accent);
                transform: translateY(-2px);
            }

            .module-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 10px;
            }

            .module-name {
                font-size: 16px;
                font-weight: 500;
                flex: 1;
            }

            .toggle-switch {
                width: 40px;
                height: 20px;
                background: #555;
                border-radius: 10px;
                position: relative;
                cursor: pointer;
                transition: background 0.2s ease;
                margin-right: 10px;
            }

            .toggle-switch::before {
                content: '';
                position: absolute;
                width: 16px;
                height: 16px;
                background: white;
                border-radius: 50%;
                top: 2px;
                left: 2px;
                transition: transform 0.2s ease;
            }

            .toggle-switch.enabled {
                background: var(--accent);
            }

            .toggle-switch.enabled::before {
                transform: translateX(20px);
            }

            /* 配置按钮样式 */
            .config-btn {
                cursor: pointer;
                padding: 6px 12px;
                border-radius: 4px;
                background: var(--secondary-bg);
                color: var(--text-primary);
                font-size: 12px;
                transition: all 0.2s ease;
                user-select: none;
                border: 1px solid var(--border);
            }

            .config-btn:hover {
                background: var(--accent);
                color: white;
            }

            .module-description {
                color: var(--text-secondary);
                font-size: 12px;
                margin-bottom: 10px;
            }

            /* 弹窗样式 */
            .modal-overlay {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.7);
                display: none;
                justify-content: center;
                align-items: center;
                z-index: 1000;
            }

            .modal-overlay.active {
                display: flex;
            }

            .modal-content {
                background: var(--secondary-bg);
                border-radius: var(--radius);
                padding: 20px;
                width: 90%;
                max-width: 500px;
                max-height: 80vh;
                overflow-y: auto;
                border: 1px solid var(--border);
            }

            .modal-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 15px;
                border-bottom: 1px solid var(--border);
                padding-bottom: 10px;
            }

            .modal-title {
                font-size: 18px;
                font-weight: 500;
            }

            .close-btn {
                cursor: pointer;
                font-size: 20px;
                color: var(--text-secondary);
                transition: color 0.2s ease;
            }

            .close-btn:hover {
                color: var(--error);
            }

            .modal-settings {
                margin-top: 15px;
            }

            .setting-item {
                margin-bottom: 15px;
            }

            .setting-label {
                display: block;
                font-size: 14px;
                color: var(--text-secondary);
                margin-bottom: 8px;
            }

            .setting-input {
                width: 100%;
                padding: 10px 12px;
                background: var(--primary-bg);
                border: 1px solid var(--border);
                color: var(--text-primary);
                border-radius: var(--radius-sm);
                font-size: 14px;
            }

            .setting-input:focus {
                outline: none;
                border-color: var(--accent);
            }

            /* 布尔值开关 */
            .bool-toggle {
                width: 44px;
                height: 22px;
            }

            .bool-toggle::before {
                width: 18px;
                height: 18px;
                top: 2px;
                left: 2px;
            }

            /* 状态栏 */
            .status-bar {
                background: var(--secondary-bg);
                padding: 10px 20px;
                border-top: 1px solid var(--border);
                font-size: 12px;
                color: var(--text-secondary);
                display: flex;
                justify-content: space-between;
            }

            .connection-status {
                font-weight: bold;
            }

            .status-success {
                color: var(--success);
            }

            .status-error {
                color: var(--error);
            }

            /* 更新指示器 */
            .update-indicator {
                position: fixed;
                top: 10px;
                right: 10px;
                background: var(--accent);
                color: white;
                padding: 5px 10px;
                border-radius: var(--radius);
                font-size: 12px;
                z-index: 1001;
                display: none;
                animation: fadeInOut 2s ease-in-out;
            }

            @keyframes fadeInOut {
                0% { opacity: 0; transform: translateY(-10px); }
                20% { opacity: 1; transform: translateY(0); }
                80% { opacity: 1; transform: translateY(0); }
                100% { opacity: 0; transform: translateY(-10px); }
            }

            /* 滚动条 */
            ::-webkit-scrollbar {
                width: 6px;
            }

            ::-webkit-scrollbar-track {
                background: var(--primary-bg);
            }

            ::-webkit-scrollbar-thumb {
                background: var(--border);
                border-radius: 3px;
            }

            ::-webkit-scrollbar-thumb:hover {
                background: var(--accent);
            }
        </style>
    </head>
    <body>
        <div class="header">
            Lizz GUI
        </div>

        <div class="main-container">
            <!-- 侧边栏 -->
            <div class="sidebar" id="sidebar">
                <!-- 分类将通过JavaScript动态生成 -->
            </div>

            <!-- 主内容区 -->
            <div class="content-area">
                <div class="search-box">
                    <input type="text" class="search-input" placeholder="搜索模块..." id="searchInput">
                </div>
                <div class="modules-container" id="modulesContainer">
                    <!-- 模块将通过JavaScript动态生成 -->
                </div>
            </div>
        </div>

        <!-- 配置弹窗 -->
        <div class="modal-overlay" id="configModal">
            <div class="modal-content">
                <div class="modal-header">
                    <h3 class="modal-title" id="modalTitle">模块配置</h3>
                    <span class="close-btn" id="closeModal">&times;</span>
                </div>
                <div class="modal-settings" id="modalSettings">
                    <!-- 配置内容将通过JavaScript动态生成 -->
                </div>
            </div>
        </div>

        <!-- 更新指示器 -->
        <div class="update-indicator" id="updateIndicator">配置已更新</div>

        <!-- 状态栏 -->
        <div class="status-bar">
            <div class="connection-status" id="connectionStatus">连接状态: 连接中...</div>
            <div id="moduleCount">模块数量: 0</div>
            <div id="lastUpdate">最后更新: -</div>
        </div>

        <script>
            // 全局变量
            let modules = [];
            let currentCategory = 'combat';
            let activeModalModule = null;
            let updateInterval = null;
            let isUpdating = false;

            // DOM加载完成后初始化
            document.addEventListener('DOMContentLoaded', function() {
                loadModules();
                setupEventHandlers();
                startUpdateChecker();
            });

            // 启动更新检查器（1秒间隔）
            function startUpdateChecker() {
                if (updateInterval) {
                    clearInterval(updateInterval);
                }
                
                updateInterval = setInterval(checkForUpdates, 1000); // 1秒检查一次
            }

            // 检查更新
            async function checkForUpdates() {
                if (isUpdating) return; // 防止重复更新
                
                try {
                    const response = await fetch('/api/check-updates');
                    const data = await response.json();
                    
                    if (data.hasChanges) {
                        // 显示更新指示器
                        showUpdateIndicator();
                        
                        // 重新加载模块数据
                        await loadModules();
                        
                        // 如果弹窗打开，更新弹窗内容
                        if (activeModalModule) {
                            const module = modules.find(m => m.name === activeModalModule.name);
                            if (module) {
                                document.getElementById('modalSettings').innerHTML = createModuleSettings(module);
                            }
                        }
                    }
                } catch (error) {
                    console.error('检查更新失败:', error);
                    updateConnectionStatus(false);
                }
            }

            // 显示更新指示器
            function showUpdateIndicator() {
                const indicator = document.getElementById('updateIndicator');
                indicator.style.display = 'block';
                
                // 2秒后隐藏
                setTimeout(() => {
                    indicator.style.display = 'none';
                }, 2000);
            }

            // 加载模块数据
            async function loadModules() {
                isUpdating = true;
                try {
                    const response = await fetch('/api/modules');
                    const newModules = await response.json();
                    
                    // 智能更新：只更新有变化的模块
                    updateModulesSmartly(newModules);
                    updateStatus();
                } catch (error) {
                    console.error('加载模块失败:', error);
                    updateConnectionStatus(false);
                } finally {
                    isUpdating = false;
                }
            }

            // 智能更新模块（避免不必要的DOM操作）
            function updateModulesSmartly(newModules) {
                // 如果模块数量变化，完全重新渲染
                if (newModules.length !== modules.length) {
                    modules = newModules;
                    renderSidebar();
                    renderModules();
                    return;
                }
                
                // 检查当前分类的模块是否有变化
                const currentModules = modules.filter(m => m.category === currentCategory);
                const newCurrentModules = newModules.filter(m => m.category === currentCategory);
                
                let hasChanges = false;
                
                // 检查每个模块的状态和参数
                for (let i = 0; i < currentModules.length; i++) {
                    const oldModule = currentModules[i];
                    const newModule = newCurrentModules.find(m => m.name === oldModule.name);
                    
                    if (!newModule) continue;
                    
                    // 检查模块状态变化
                    if (oldModule.state !== newModule.state) {
                        hasChanges = true;
                        // 更新开关状态
                        const switchElement = document.querySelector('[data-module="' + oldModule.name + '"] .toggle-switch');
                        if (switchElement) {
                            switchElement.classList.toggle('enabled', newModule.state);
                        }
                    }
                    
                    // 检查参数值变化（如果弹窗打开）
                    if (activeModalModule && activeModalModule.name === oldModule.name) {
                        let settingsChanged = false;
                        
                        // 检查每个参数值
                        for (let j = 0; j < oldModule.values.length; j++) {
                            const oldValue = oldModule.values[j];
                            const newValue = newModule.values.find(v => v.name === oldValue.name);
                            
                            if (newValue && oldValue.value !== newValue.value) {
                                settingsChanged = true;
                                break;
                            }
                        }
                        
                        if (settingsChanged) {
                            document.getElementById('modalSettings').innerHTML = createModuleSettings(newModule);
                        }
                    }
                }
                
                // 更新模块数据
                modules = newModules;
                
                // 如果有变化但不需要完全重渲染，只更新状态栏
                if (!hasChanges) {
                    updateStatus();
                }
            }

            // 渲染侧边栏分类
            function renderSidebar() {
                const sidebar = document.getElementById('sidebar');
                const categories = [...new Set(modules.map(m => m.category))];
                
                sidebar.innerHTML = categories.map(category => 
                    '<div class="category-item ' + (category === currentCategory ? 'active' : '') + 
                    '" onclick="selectCategory(\'' + category + '\')">' + category + '</div>'
                ).join('');
            }

            // 选择分类
            function selectCategory(category) {
                currentCategory = category;
                renderSidebar();
                renderModules();
            }

            // 渲染模块
            function renderModules() {
                const container = document.getElementById('modulesContainer');
                const filteredModules = modules.filter(m => m.category === currentCategory);
                
                container.innerHTML = '<div class="module-grid">' + 
                    filteredModules.map(module => createModuleCard(module)).join('') + 
                    '</div>';
            }

            // 创建模块卡片
            function createModuleCard(module) {
                return '<div class="module-card" data-module="' + module.name + '">' +
                    '<div class="module-header">' +
                    '<div class="module-name">' + module.name + '</div>' +
                    '<div style="display: flex; align-items: center;">' +
                    '<div class="toggle-switch ' + (module.state ? 'enabled' : '') + 
                    '" onclick="toggleModuleState(\'' + module.name + '\', event)"></div>' +
                    '<div class="config-btn" onclick="openConfigModal(\'' + module.name + '\')">配置</div>' +
                    '</div>' +
                    '</div>' +
                    '<div class="module-description">' + module.description + '</div>' +
                    '</div>';
            }

            // 打开配置弹窗
            function openConfigModal(moduleName) {
                const module = modules.find(m => m.name === moduleName);
                if (!module) return;

                activeModalModule = module;
                document.getElementById('modalTitle').textContent = module.name + ' - 配置';
                document.getElementById('modalSettings').innerHTML = createModuleSettings(module);
                document.getElementById('configModal').classList.add('active');
            }

            // 关闭配置弹窗
            function closeConfigModal() {
                document.getElementById('configModal').classList.remove('active');
                activeModalModule = null;
            }

            // 创建模块设置
            function createModuleSettings(module) {
                if (!module.values || module.values.length === 0) {
                    return '<div style="text-align: center; color: var(--text-secondary); padding: 20px;">该模块没有可配置的参数</div>';
                }

                return module.values.map(value => createSettingItem(value, module.name)).join('');
            }

            // 创建设置项
            function createSettingItem(value, moduleName) {
                let inputHtml = '';
                
                switch (value.type) {
                    case 'BoolValue':
                        inputHtml = '<div class="toggle-switch bool-toggle ' + (value.value === 'true' ? 'enabled' : '') + 
                            '" onclick="toggleSettingValue(\'' + moduleName + '\', \'' + value.name + '\', event)"></div>';
                        break;
                    case 'IntValue':
                    case 'FloatValue':
                        inputHtml = '<input type="number" class="setting-input" value="' + value.value + 
                            '" onchange="updateSettingValue(\'' + moduleName + '\', \'' + value.name + '\', this.value)"' +
                            (value.min ? ' min="' + value.min + '"' : '') +
                            (value.max ? ' max="' + value.max + '"' : '') + '>';
                        break;
                    case 'TextValue':
                        inputHtml = '<input type="text" class="setting-input" value="' + value.value + 
                            '" onchange="updateSettingValue(\'' + moduleName + '\', \'' + value.name + '\', this.value)">';
                        break;
                    case 'ListValue':
                        if (value.choices) {
                            inputHtml = '<select class="setting-input" onchange="updateSettingValue(\'' + moduleName + '\', \'' + value.name + '\', this.value)">' +
                                value.choices.map(choice => 
                                    '<option value="' + choice + '" ' + (choice === value.value ? 'selected' : '') + '>' + choice + '</option>'
                                ).join('') + '</select>';
                        }
                        break;
                    case 'ColorValue':
                        // 确保颜色值使用正确的十六进制格式
                        const colorValue = value.value;
                        let hexColor;
                        if (typeof colorValue === 'number') {
                            // 如果是数字，转换为十六进制
                            hexColor = '#' + (colorValue & 0xFFFFFF).toString(16).padStart(6, '0');
                        } else if (colorValue.startsWith('#')) {
                            // 如果已经是十六进制，直接使用
                            hexColor = colorValue;
                        } else {
                            // 其他格式，尝试解析
                            hexColor = '#000000';
                        }
                        inputHtml = '<input type="color" class="setting-input" value="' + hexColor + 
                            '" onchange="updateColorValue(\'' + moduleName + '\', \'' + value.name + '\', this.value)" style="height: 40px;">';
                        break;
                    default:
                        inputHtml = '<input type="text" class="setting-input" value="' + value.value + 
                            '" onchange="updateSettingValue(\'' + moduleName + '\', \'' + value.name + '\', this.value)">';
                }

                return '<div class="setting-item">' +
                    '<label class="setting-label">' + value.name + '</label>' +
                    inputHtml +
                    '</div>';
            }

            // 切换模块状态（仅主开关）
            async function toggleModuleState(moduleName, event) {
                event.stopPropagation(); // 防止事件冒泡
                const module = modules.find(m => m.name === moduleName);
                if (!module) return;

                const newState = !module.state;
                await updateModule(moduleName, newState);
                
                // 更新UI
                const switchElement = event.target;
                switchElement.classList.toggle('enabled', newState);
                
                // 更新模块数据
                module.state = newState;
            }

            // 切换设置值（参数布尔值，不改变模块状态）
            async function toggleSettingValue(moduleName, valueName, event) {
                event.stopPropagation(); // 防止事件冒泡
                const currentValue = event.target.classList.contains('enabled');
                const newValue = !currentValue;
                
                await updateModuleValue(moduleName, valueName, newValue.toString());
                
                // 更新UI
                event.target.classList.toggle('enabled', newValue);
                
                // 更新模块数据（仅更新参数值，不改变模块状态）
                const module = modules.find(m => m.name === moduleName);
                if (module) {
                    const value = module.values.find(v => v.name === valueName);
                    if (value) {
                        value.value = newValue.toString();
                    }
                }
            }

            // 更新设置值
            async function updateSettingValue(moduleName, valueName, newValue) {
                await updateModuleValue(moduleName, valueName, newValue);
                
                // 更新模块数据
                const module = modules.find(m => m.name === moduleName);
                if (module) {
                    const value = module.values.find(v => v.name === valueName);
                    if (value) {
                        value.value = newValue;
                    }
                }
            }

            // 更新颜色值（特殊处理RGB颜色）
            async function updateColorValue(moduleName, valueName, newValue) {
                // 将十六进制颜色转换为整数发送给后端
                const colorInt = parseInt(newValue.substring(1), 16);
                await updateModuleValue(moduleName, valueName, colorInt.toString());
                
                // 更新模块数据
                const module = modules.find(m => m.name === moduleName);
                if (module) {
                    const value = module.values.find(v => v.name === valueName);
                    if (value) {
                        value.value = newValue; // 保持十六进制格式用于显示
                    }
                }
            }

            // 更新模块状态
            async function updateModule(moduleName, state) {
                try {
                    await fetch('/api/update', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ moduleName: moduleName, state: state })
                    });
                    updateStatus();
                } catch (error) {
                    console.error('更新模块失败:', error);
                }
            }

            // 更新模块参数值
            async function updateModuleValue(moduleName, valueName, value) {
                try {
                    await fetch('/api/update', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ 
                            moduleName: moduleName, 
                            valueName: valueName, 
                            value: value 
                        })
                    });
                    updateStatus();
                } catch (error) {
                    console.error('更新参数失败:', error);
                }
            }

            // 设置事件处理器
            function setupEventHandlers() {
                // 搜索功能
                document.getElementById('searchInput').addEventListener('input', debounce(filterModules, 300));
                
                // 关闭弹窗
                document.getElementById('closeModal').addEventListener('click', closeConfigModal);
                document.getElementById('configModal').addEventListener('click', function(event) {
                    if (event.target === this) {
                        closeConfigModal();
                    }
                });
                
                // ESC键关闭弹窗
                document.addEventListener('keydown', function(event) {
                    if (event.key === 'Escape') {
                        closeConfigModal();
                    }
                });
            }

            // 过滤模块
            function filterModules() {
                const searchTerm = document.getElementById('searchInput').value.toLowerCase();
                const container = document.getElementById('modulesContainer');
                
                if (!searchTerm) {
                    renderModules();
                    return;
                }
                
                const filteredModules = modules.filter(m => 
                    m.name.toLowerCase().includes(searchTerm) || 
                    m.description.toLowerCase().includes(searchTerm)
                );
                
                container.innerHTML = '<div class="module-grid">' + 
                    filteredModules.map(module => createModuleCard(module)).join('') + 
                    '</div>';
            }

            // 更新状态
            function updateStatus() {
                document.getElementById('moduleCount').textContent = '模块数量: ' + modules.length;
                document.getElementById('lastUpdate').textContent = '最后更新: ' + new Date().toLocaleTimeString();
                updateConnectionStatus(true);
            }

            // 更新连接状态
            function updateConnectionStatus(connected) {
                const statusElement = document.getElementById('connectionStatus');
                statusElement.textContent = '连接状态: ' + (connected ? '已连接' : '连接失败');
                statusElement.className = 'connection-status ' + (connected ? 'status-success' : 'status-error');
            }

            // 防抖函数
            function debounce(func, wait) {
                let timeout;
                return function executedFunction(...args) {
                    const later = () => {
                        clearTimeout(timeout);
                        func(...args);
                    };
                    clearTimeout(timeout);
                    timeout = setTimeout(later, wait);
                };
            }
        </script>
    </body>
    </html>
        """.trimIndent()
    }
}