package com.sbs.restartapp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RebootAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Accessibility Service Unbound")
        instance = null
        return super.onUnbind(intent)
    }

    private var isClickScheduled = false

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isRebootPending) return

        if (!isClickScheduled) {
            isClickScheduled = true
            Log.d(TAG, "Power menu detected. Scheduling auto click on Restart in 5 seconds...")
            handler.postDelayed({
                performAutoClick()
            }, 5000)
        }
    }

    private fun performAutoClick() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.d(TAG, "Root node is null, retrying once in 500ms...")
            handler.postDelayed({
                val retryRootNode = rootInActiveWindow
                if (retryRootNode != null) {
                    executeClickLogic(retryRootNode)
                } else {
                    isClickScheduled = false
                }
            }, 500)
            return
        }
        executeClickLogic(rootNode)
    }

    private fun executeClickLogic(rootNode: AccessibilityNodeInfo) {
        val koreanNodes = rootNode.findAccessibilityNodeInfosByText("다시 시작")
        val englishNodes = rootNode.findAccessibilityNodeInfosByText("Restart")
        val rebootNodes = koreanNodes + englishNodes

        var clicked = false
        for (node in rebootNodes) {
            if (node == null) continue
            if (clickNodeOrParent(node)) {
                Log.d(TAG, "Successfully clicked restart node: ${node.text}")
                clicked = true
                break
            }
        }

        if (clicked) {
            // 삼성 등 일부 단말의 2단계 터치(한번 더 누르기) 대응을 위해 1.5초 후 2차 클릭 감행
            handler.postDelayed({
                val secondRootNode = rootInActiveWindow
                if (secondRootNode != null) {
                    val secondRebootNodes = secondRootNode.findAccessibilityNodeInfosByText("다시 시작") + 
                                            secondRootNode.findAccessibilityNodeInfosByText("Restart")
                    for (node in secondRebootNodes) {
                        if (node == null) continue
                        if (clickNodeOrParent(node)) {
                            Log.d(TAG, "Successfully clicked second confirmation restart node: ${node.text}")
                            break
                        }
                    }
                }
                isRebootPending = false
                isClickScheduled = false
            }, 1500)
        } else {
            Log.d(TAG, "Could not find Restart node on screen.")
            isClickScheduled = false
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var tempNode: AccessibilityNodeInfo? = node
        while (tempNode != null) {
            if (tempNode.isClickable) {
                val result = tempNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (result) {
                    return true
                }
            }
            tempNode = tempNode.parent
        }
        return false
    }

    private fun resetPendingRebootDelayed() {
        handler.removeCallbacks(resetRunnable)
        handler.postDelayed(resetRunnable, 5000)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    fun triggerPowerMenu() {
        Log.d(TAG, "Triggering Power Menu Dialog")
        isRebootPending = true
        isClickScheduled = false
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        
        handler.removeCallbacks(resetRunnable)
        handler.postDelayed(resetRunnable, 15000)
    }

    companion object {
        private const val TAG = "RebootAccessService"
        
        @Volatile
        var instance: RebootAccessibilityService? = null
            private set
            
        @Volatile
        var isRebootPending = false
            private set

        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private val resetRunnable = Runnable {
            if (isRebootPending) {
                Log.d(TAG, "Reboot pending state timed out/completed. Resetting.")
                isRebootPending = false
            }
        }
    }
}
