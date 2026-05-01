import React, { useState } from 'react';
import { Bell, Fullscreen, LogOut, Menu, UserRound } from 'lucide-react';

export function TopBar({ breadcrumb, collapsed, onToggleSide, onLogout }) {
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  async function toggleFullScreen() {
    if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
      await document.documentElement.requestFullscreen();
    } else if (document.exitFullscreen) {
      await document.exitFullscreen();
    }
  }

  return (
    <div className="topBar">
      <div className="topLeft">
        <button className="iconButton" onClick={onToggleSide} aria-label={collapsed ? '展开侧栏' : '折叠侧栏'}>
          <Menu size={18} />
        </button>
        <div className="breadcrumb">
          {breadcrumb.map((item, index) => (
            <React.Fragment key={`${item}-${index}`}>
              {index > 0 && <b>/</b>}
              <span className={index === breadcrumb.length - 1 ? 'current' : ''}>{item}</span>
            </React.Fragment>
          ))}
        </div>
      </div>
      <div className="topActions">
        <button className="iconButton" onClick={toggleFullScreen} aria-label="全屏">
          <Fullscreen size={17} />
        </button>
        <div className="popoverWrap">
          <button className="iconButton" onClick={() => setNoticeOpen((value) => !value)} aria-label="通知">
            <Bell size={17} />
            <i className="dot" />
          </button>
          {noticeOpen && <div className="miniPopover">暂无新的系统通知</div>}
        </div>
        <div className="popoverWrap">
          <button className="avatarButton" onClick={() => setMenuOpen((value) => !value)} aria-label="头像菜单">
            <UserRound size={17} />
          </button>
          {menuOpen && (
            <div className="miniPopover menuPopover">
              <button className="plainAction" onClick={onLogout}><LogOut size={15} />退出登录</button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
