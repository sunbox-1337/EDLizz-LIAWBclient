/*
 * 滚木。
 *
 * 纯搞怪模块：没有功能、没有事件、没有设置。
 * 原本这里 import 了全项目 360 个模块（整活），但其中若干模块名在当前分支已经不存在，
 * 全量编译会直接报 unresolved reference —— 这里只保留模块本体需要的两个 import。
 */
package net.ccbluex.liquidbounce.features.module.modules.addons

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module

object Grim : Module("滚木。", Category.FUN) {

    // 一行功能都没有。滚木嘛，躺着就好。
}
