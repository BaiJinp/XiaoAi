import type { TreeProps } from 'antd';
import type { MenuProps } from 'antd/es/menu';
import React from 'react';
import { type FolderTreeData } from './DirectoryTree';
export type { FolderTreeData };
export interface FileContentService {
    loadFileContent(filePath: string): Promise<string>;
}
export type SemanticType = 'root' | 'directoryTree' | 'directoryTitle' | 'filePreview' | 'previewTitle' | 'previewRender';
export interface FolderProps {
    prefixCls?: string;
    className?: string;
    classNames?: Partial<Record<SemanticType, string>>;
    styles?: Partial<Record<SemanticType, React.CSSProperties>>;
    style?: React.CSSProperties;
    directoryIcons?: false | Record<'directory' | string, React.ReactNode | (() => React.ReactNode)>;
    treeData: FolderTreeData[];
    selectable?: boolean;
    selectedFile?: string[];
    defaultSelectedFile?: string[];
    onSelectedFileChange?: (file: {
        path: string[];
        title?: FolderTreeData['title'];
        content?: string;
    }) => void;
    directoryTreeWith?: number | string;
    emptyRender?: false | React.ReactNode | (() => React.ReactNode);
    previewRender?: React.ReactNode | ((file: {
        content?: string;
        path: string[];
        title?: FolderTreeData['title'];
        language: string;
    }, info: {
        originNode: React.ReactNode;
    }) => React.ReactNode);
    defaultExpandedPaths?: string[];
    expandedPaths?: string[];
    defaultExpandAll?: boolean;
    onExpandedPathsChange?: (paths: string[]) => void;
    fileContentService?: FileContentService;
    onFileClick?: (filePath: string, content?: string) => void;
    onFolderClick?: (folderPath: string) => void;
    directoryTitle?: false | React.ReactNode | (() => React.ReactNode);
    previewTitle?: false | React.ReactNode | (({ title, path, content, }: {
        title: React.ReactNode;
        path: string[];
        content: string;
    }) => React.ReactNode);
    /** Right-click context menu items, applies to all nodes. Can be overridden by `contextMenu` in `FolderTreeData`. Function form receives the node data and full path key */
    contextMenu?: MenuProps['items'] | ((node: FolderTreeData, key: string) => MenuProps['items']);
    /** Callback when right-clicking a node */
    onRightClick?: TreeProps['onRightClick'];
}
export type FolderRef = {
    nativeElement: HTMLDivElement;
    /** Get a node by path segments, returns undefined if not found */
    getNode: (path: string[]) => FolderTreeData | undefined;
    /** Immutable update: merge partial data into the target node, returns new treeData */
    updateNode: (path: string[], data: Partial<FolderTreeData>) => FolderTreeData[];
    /** Immutable update: delete the target node, returns new treeData */
    deleteNode: (path: string[]) => FolderTreeData[];
    /** Immutable update: add a child node under the target folder, returns new treeData */
    addNode: (parentPath: string[], node: FolderTreeData) => FolderTreeData[];
};
declare const Folder: React.ForwardRefExoticComponent<FolderProps & React.RefAttributes<FolderRef>>;
export default Folder;
