import { FileOutlined, FolderOutlined } from '@ant-design/icons';
import { Dropdown, Tree } from 'antd';
import clsx from 'clsx';
import React, { useCallback, useRef, useState } from 'react';
import { useXProviderContext } from "../x-provider";

// File tree node type

const {
  DirectoryTree: AntDirectoryTree
} = Tree;
const DirectoryTree = ({
  treeData,
  selectedKeys,
  expandedKeys,
  onSelect,
  onExpand,
  showLine = false,
  defaultExpandAll = true,
  className,
  classNames,
  directoryIcons,
  styles,
  style,
  directoryTitle,
  prefixCls: customizePrefixCls,
  contextMenu,
  onRightClick
}) => {
  // ============================ Right-click Menu ============================
  const [contextMenuOpen, setContextMenuOpen] = useState(false);
  const [contextMenuItems, setContextMenuItems] = useState(undefined);

  // Store all original node data indexed by key for quick lookup
  const nodeDataMapRef = useRef(new Map());

  // Track right-click state to prevent onSelect from firing during right-click
  const isRightClickRef = useRef(false);

  // ============================ Tree Config ============================
  const isFolder = node => {
    return !!node.children && node.children.length > 0;
  };
  const getIcon = useCallback(node => {
    // If directoryIcons is false or null, do not display icons
    if (directoryIcons === false || directoryIcons === null) {
      return null;
    }
    if (isFolder(node)) {
      const icon = directoryIcons?.directory;
      if (typeof icon === 'function') {
        return icon();
      }
      return icon || /*#__PURE__*/React.createElement(FolderOutlined, null);
    }

    // Return corresponding icon based on file extension
    const filePath = node.path.toLowerCase();
    const extension = filePath.split('.').pop();
    if (extension) {
      // Check if custom icon configuration exists
      const icon = directoryIcons?.[extension];
      if (icon) {
        return typeof icon === 'function' ? icon() : icon;
      }
    }
    return /*#__PURE__*/React.createElement(FileOutlined, null);
  }, [directoryIcons]);
  const buildPathSegments = useCallback((node, parentSegments = []) => {
    if (node.path === '/' && parentSegments.length === 0) {
      return ['/'];
    }
    return [...parentSegments, node.path].filter(segment => segment !== '');
  }, []);
  const convertToTreeData = useCallback((nodes, parentSegments = []) => {
    return nodes.map(node => {
      const pathSegments = buildPathSegments(node, parentSegments);
      const fullPath = pathSegments.join('/').replace(/^\/+/, '');
      // Store original node data for context menu lookup
      nodeDataMapRef.current.set(fullPath, node);
      return {
        ...node,
        key: fullPath,
        path: fullPath,
        pathSegments,
        title: node.title,
        icon: getIcon(node),
        isLeaf: !isFolder(node),
        children: node.children ? convertToTreeData(node.children, pathSegments) : undefined
      };
    });
  }, [buildPathSegments, getIcon]);
  const treeDataConverted = convertToTreeData(treeData);
  const titleNode = directoryTitle === false || directoryTitle === null ? null : typeof directoryTitle === 'function' ? directoryTitle() : directoryTitle;
  // ============================ Prefix ============================
  const {
    getPrefixCls
  } = useXProviderContext();
  const prefixCls = getPrefixCls('folder', customizePrefixCls);

  // ============================ Right-click Handler ============================
  const handleRightClick = info => {
    const {
      node
    } = info;
    const nodeKey = node.key;
    const originalNode = nodeDataMapRef.current.get(nodeKey);

    // Mark as right-click to prevent onSelect from firing
    isRightClickRef.current = true;

    // Determine context menu items: node-level > global function > global items
    let items;
    if (originalNode?.contextMenu === false) {
      // Node explicitly disables context menu
      items = undefined;
    } else if (originalNode?.contextMenu) {
      // Node has custom context menu
      items = typeof originalNode.contextMenu === 'function' ? originalNode.contextMenu(nodeKey) : originalNode.contextMenu;
    } else if (contextMenu) {
      // Use global contextMenu
      items = typeof contextMenu === 'function' ? contextMenu(originalNode || {}, nodeKey) : contextMenu;
    }
    if (items && items.length > 0) {
      setContextMenuItems(items);
      setContextMenuOpen(true);
    } else {
      // No menu to show, reset right-click flag immediately
      isRightClickRef.current = false;
    }
    onRightClick?.(info);
  };

  // Intercept onSelect to skip selection triggered by right-click
  const handleSelect = (keys, info) => {
    if (isRightClickRef.current) {
      isRightClickRef.current = false;
      return;
    }
    onSelect?.(keys, info);
  };

  // Reset right-click flag when context menu closes
  const handleContextMenuOpenChange = open => {
    if (open && !isRightClickRef.current) {
      return;
    }
    setContextMenuOpen(open);
    if (!open) {
      isRightClickRef.current = false;
    }
  };
  return /*#__PURE__*/React.createElement(React.Fragment, null, titleNode ? /*#__PURE__*/React.createElement("div", {
    style: {
      ...styles?.directoryTitle,
      ...style
    },
    className: clsx(`${prefixCls}-directory-tree-title`, className, classNames?.directoryTitle)
  }, titleNode) : null, /*#__PURE__*/React.createElement(Dropdown, {
    menu: {
      items: contextMenuItems || []
    },
    open: contextMenuOpen,
    onOpenChange: handleContextMenuOpenChange,
    trigger: ['contextMenu']
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      height: '100%'
    }
  }, /*#__PURE__*/React.createElement(AntDirectoryTree, {
    treeData: treeDataConverted,
    selectedKeys: selectedKeys,
    expandedKeys: expandedKeys,
    onSelect: handleSelect,
    onExpand: onExpand,
    onRightClick: handleRightClick,
    multiple: false,
    blockNode: true,
    classNames: {
      itemTitle: `${prefixCls}-directory-tree-item-title`
    },
    showLine: showLine,
    defaultExpandAll: defaultExpandAll,
    className: clsx(`${prefixCls}-directory-tree-content`)
  }))));
};
export default DirectoryTree;