package com.github.kr328.clash

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.*
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class NodeListActivity : BaseActivity<NodeListDesign>() {
    override suspend fun main() {
        val mode = withClash { queryOverride(Clash.OverrideSlot.Session).mode }
        val namesTmp = withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
        var names = List(1) { namesTmp[0] }

        val states = List(names.size) { ProxyState("?") }
        val unorderedStates = names.indices.map { names[it] to states[it] }.toMap()
        val reloadLock = Semaphore(10)

        val design = NodeListDesign(this,
            mode,
            names,
            uiStore)

        setContentDesign(design)

        design.requests.send(NodeListDesign.Request.FetchNodeList(0))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            val files = withContext(Dispatchers.IO) {
                                // 获取节点列表
                                // 增加Profile配置， url: http://sub.free88.top/config.yaml
                                // 显示节点列表

                                Log.e("启动NodeList界面...")


                            }

                            //design.patchLogs(files)
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        is NodeListDesign.Request.FetchNodeList -> {
                            launch {
                                Log.e("开始获取节点列表")
                                val group = reloadLock.withPermit {
                                    withClash {
                                        queryProxyGroup(names[it.index], uiStore.proxySort)
                                    }
                                }
                                val state = states[it.index]

                                state.now = group.now

                                design.updateGroup(
                                    it.index,
                                    group.proxies,
                                    group.type == Proxy.Type.Selector,
                                    state,
                                    unorderedStates
                                )
                                Log.e("完成获取节点列表")
                            }
                        }
                        is NodeListDesign.Request.SelectNode -> {
                            Log.e("选择的节点是：")
                            withClash {
                                patchSelector(names[it.index], it.name)

                                states[it.index].now = it.name
                            }
                            design.requestRedrawVisible()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    /*private suspend fun NodeListDesign.verifyAndCommit() {
        when {
            profile.name.isBlank() -> {
                showToast(R.string.empty_name, ToastDuration.Long)
            }
            profile.type != Profile.Type.File && profile.source.isBlank() -> {
                showToast(R.string.invalid_url, ToastDuration.Long)
            }
            else -> {
                try {
                    withProcessing { updateStatus ->
                        withProfile {
                            patch(profile.uuid, profile.name, profile.source, profile.interval)

                            coroutineScope {
                                commit(profile.uuid) {
                                    launch {
                                        updateStatus(it)
                                    }
                                }
                            }
                        }
                    }

                    setResult(RESULT_OK)

                    finish()
                } catch (e: Exception) {
                    showExceptionToast(e)
                }
            }
        }
    }*/

}