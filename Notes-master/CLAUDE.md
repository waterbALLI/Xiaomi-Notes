# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

MiCode便签是小米便签的社区开源版，是一个Android便签应用。项目使用Apache License 2.0许可证。

## 项目结构

### 包结构
- `net.micode.notes.data` - 数据层，包含数据库定义、ContentProvider和数据结构
- `net.micode.notes.ui` - 用户界面层，包含Activity和Fragment
- `net.micode.notes.model` - 业务模型层
- `net.micode.notes.tool` - 工具类
- `net.micode.notes.widget` - 桌面小部件
- `net.micode.notes.gtask` - Google Tasks同步功能

### 核心组件
1. **NotesListActivity** - 主界面，显示便签列表
2. **NoteEditActivity** - 便签编辑界面
3. **NotesProvider** - ContentProvider，管理便签数据存储
4. **NoteWidgetProvider_2x** 和 **NoteWidgetProvider_4x** - 桌面小部件
5. **GTaskSyncService** - Google Tasks同步服务

### 数据模型
- **Notes** - 定义便签和文件夹的类型、常量、URI和数据库列
- **TextNote** - 文本便签数据结构
- **CallNote** - 通话记录便签数据结构
- **Note** - 便签业务模型
- **WorkingNote** - 正在编辑的便签模型

## 构建和开发

### 构建系统
这是一个较老的Android项目，可能使用Ant构建系统（基于`.gitignore`中的`project.properties`和`.classpath`文件推断）。

### 关键文件
- `AndroidManifest.xml` - 应用配置，定义权限、Activity、Service等
- `res/` - 资源文件（布局、字符串、图片等）
- `src/` - Java源代码

### 数据库架构
应用使用SQLite数据库，通过ContentProvider (`NotesProvider`) 访问。主要表包括：
- `note` 表 - 存储便签和文件夹的元数据
- `data` 表 - 存储便签的具体内容

## 功能特性

1. **便签管理** - 创建、编辑、删除文本便签
2. **文件夹组织** - 将便签分类到不同文件夹
3. **通话记录便签** - 自动创建通话记录
4. **桌面小部件** - 2x2和4x4两种尺寸的小部件
5. **提醒功能** - 为便签设置提醒
6. **Google Tasks同步** - 与Google Tasks服务同步
7. **搜索功能** - 搜索便签内容
8. **备份恢复** - 便签数据备份和恢复

## 权限需求
应用需要以下权限：
- 外部存储写入（备份功能）
- 互联网访问（Google Tasks同步）
- 读取联系人（通话记录功能）
- 账户管理（Google账户同步）
- 开机启动（提醒功能）

## 开发注意事项

### 代码风格
- 使用Java编程语言
- 遵循Android开发规范
- 包名：`net.micode.notes`
- 使用ContentProvider进行数据访问

### 数据库操作
所有数据库操作应通过`NotesProvider`进行，使用定义在`Notes.java`中的URI和列常量。

### UI开发
- 使用Android原生UI组件
- 支持Android API Level 14及以上
- 使用`res/layout/`中的XML布局文件

### 同步功能
Google Tasks同步功能位于`gtask`包中，使用异步任务处理网络操作。

## 测试
项目未包含自动化测试框架。测试应通过手动运行应用进行。

## 贡献指南
- 问题跟踪：https://github.com/MiCode/Notes/issues
- 功能讨论：http://micode.net/forum.php?mod=forumdisplay&fid=38
- 遵循Apache License 2.0许可证