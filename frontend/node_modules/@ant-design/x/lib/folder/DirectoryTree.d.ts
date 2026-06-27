import type { TreeProps } from 'antd';
import type { MenuProps } from 'antd/es/menu';
import React from 'react';
import type { FolderProps } from '.';
export interface FolderTreeData {
    title: React.ReactNode;
    path: string;
    content?: string;
    children?: FolderTreeData[];
    /** Right-click context menu items, set to `false` to disable for this node. Function form receives full path key */
    contextMenu?: MenuProps['items'] | false | ((key: string) => MenuProps['items']);
}
export interface DirectoryTreeProps {
    treeData: FolderTreeData[];
    directoryIcons?: false | Record<'directory' | string, React.ReactNode | (() => React.ReactNode)>;
    selectedKeys?: string[];
    expandedKeys?: string[];
    onSelect?: TreeProps['onSelect'];
    onExpand?: TreeProps['onExpand'];
    showLine?: boolean;
    defaultExpandAll?: boolean;
    className?: string;
    classNames?: FolderProps['classNames'];
    styles?: FolderProps['styles'];
    style?: React.CSSProperties;
    directoryTitle?: FolderProps['directoryTitle'];
    prefixCls?: string;
    /** Right-click context menu items, applies to all nodes. Can be overridden by `contextMenu` in `FolderTreeData` */
    contextMenu?: MenuProps['items'] | ((node: FolderTreeData, key: string) => MenuProps['items']);
    /** Callback when right-clicking a node */
    onRightClick?: TreeProps['onRightClick'];
}
declare const DirectoryTree: React.FC<DirectoryTreeProps>;
export default DirectoryTree;
