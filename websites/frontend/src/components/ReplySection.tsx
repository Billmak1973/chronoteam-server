import React, { useState, useEffect, useCallback, useRef } from "react";

// ==========================================
// 1. 全局 Window 接口擴展 (解決 TS 報錯)
// ==========================================
declare global {
  interface Window {
    showNotification: (message: string, isError?: boolean) => void;
    showBlacklistedModal?: () => void;
    showGlobalBanModal?: (username: string, expiresAt: string) => void;
    openDeleteModal?: (message: string, onConfirm: () => Promise<void>) => void;
    openUnblockModal?: (username: string, onConfirm: (username: string) => Promise<void>) => void;
    openReportModal?: (reviewId: number, reportedUsername: string, targetType: string, content: string) => void;
    closeAdminPermissionModal?: () => void;
    showBlockedModal?: (message: string) => void;
    showLoginRequiredModal?: () => void;
    // 【新增】確認再次封禁的函數
    confirmRepeatBan?: () => void;

    // 【新增】開啟永久拉黑確認彈窗的函數
    openBlacklistConfirmModal?: (targetUsername: string, reviewId: number, reviewContent: string) => void;
  }
}

// ==========================================
// 2. 數據模型與 Props 接口定義
// ==========================================
export interface ReplyCustomer {
  username: string;
}

export interface Reply {
  id: number;
  content: string;
  createdAt: string;
  likeCount: number;
  dislikeCount: number;
  isLikedByMe: boolean;
  isDislikedByMe: boolean;
  customer: ReplyCustomer;
  replyToUser?: string | null;
}

export interface ReplySectionProps {
  reviewId: number;
  initialReplyCount: number;
  currentUsername: string;
  isAdmin: boolean;
  productId: number;
  rootUsername: string;
  onReplyCountChange?: (reviewId: number, delta: number) => void;
  showReplyForm: boolean;
  setShowReplyForm: (show: boolean) => void;
  blockedUsers: string[];
  onBlockUser: (targetUsername: string, shouldBlock: boolean) => Promise<{ success: boolean; message?: string }>;
  targetReviewId?: string | null;
  validMentionedUsers?: string[];
}

// ==========================================
// 3. 組件主體
// ==========================================
const ReplySection: React.FC<ReplySectionProps> = ({
                                                     reviewId,
                                                     initialReplyCount,
                                                     currentUsername,
                                                     isAdmin,
                                                     productId,
                                                     rootUsername,
                                                     onReplyCountChange,
                                                     showReplyForm,
                                                     setShowReplyForm,
                                                     blockedUsers,
                                                     onBlockUser,
                                                     targetReviewId,
                                                     validMentionedUsers = [],
                                                   }) => {
  // 輔助函數：安全調用全局函數
  const notify = (message: string, isError: boolean = false) => {
    if (typeof window.showNotification === "function") {
      window.showNotification(message, isError);
    } else {
      alert(message);
    }
  };

  const [isOpen, setIsOpen] = useState<boolean>(false);
  const [replies, setReplies] = useState<Reply[]>([]);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(false);
  const [sort, setSort] = useState<string>("newest");

  // 內部表單狀態
  const [replyText, setReplyText] = useState<string>("");
  const [replyToUser, setReplyToUser] = useState<string>("");
  const [replyParentId, setReplyParentId] = useState<number | null>(null);

  // 禁言模態框狀態
  const [showBlockModal, setShowBlockModal] = useState<boolean>(false);
  const [targetBlockUser, setTargetBlockUser] = useState<string>("");
  const [blockValue, setBlockValue] = useState<number>(1);
  const [blockUnit, setBlockUnit] = useState<"day" | "week" | "month">("day");

  // 用於存儲當前要封禁的評論 ID 和內容快照
  const [blockReviewId, setBlockReviewId] = useState<number | null>(null);
  const [blockReviewContent, setBlockReviewContent] = useState<string>("");

  // 刪除回覆模態框相關狀態
  const [showDeleteModal, setShowDeleteModal] = useState<boolean>(false);
  const [deleteReason, setDeleteReason] = useState<string>("inappropriate");
  const [customReason, setCustomReason] = useState<string>("");
  const [deletingReplyId, setDeletingReplyId] = useState<number | null>(null);

  // 用於記錄管理員當前會話中已封禁的用戶
  const adminBannedUsersRef = useRef<Set<string>>(new Set());

  const [localValidMentions, setLocalValidMentions] = useState<string[]>(validMentionedUsers);

  // ==========================================
  // 🛡️ 兜底邏輯：確保關閉管理員權限彈窗的全局函數存在
  // ==========================================
  useEffect(() => {
    if (typeof window.closeAdminPermissionModal !== "function") {
      window.closeAdminPermissionModal = () => {
        const modal = document.getElementById("adminPermissionModal");
        if (modal) {
          modal.style.display = "none";
          document.body.style.overflow = ""; // 恢復背景滾動
        }
      };
    }
  }, []);

  // 【核心修復 1】當 showReplyForm 變成 true 時，根據是否有回覆決定是否展開
  useEffect(() => {
    if (showReplyForm) {
      if (initialReplyCount > 0) {
        setIsOpen(true);
      } else {
        setIsOpen(false);
      }
      if (replyParentId === null && rootUsername) {
        setReplyToUser(rootUsername);
      }
    }
  }, [showReplyForm, rootUsername, initialReplyCount, replyParentId]);

  // 【新增修復】：如果帶有 targetReviewId，強制加載回覆並展開
  useEffect(() => {
    if (targetReviewId && !isOpen && initialReplyCount > 0) {
      setIsOpen(true);
      fetchReplies(0, sort);
    }
  }, [targetReviewId, initialReplyCount, sort]);

  // 【修改修復】：樓中樓分步定位邏輯
  useEffect(() => {
    if (targetReviewId && replies.length > 0) {
      const isTargetInReplies = replies.some((r) => r.id.toString() === targetReviewId);

      if (isTargetInReplies) {
        setIsOpen(true);

        setTimeout(() => {
          const rootCardEl = document.getElementById(`review-card-${reviewId}`);
          if (rootCardEl) {
            rootCardEl.scrollIntoView({ behavior: "smooth", block: "start" });
          }

          setTimeout(() => {
            const targetEl = document.getElementById(`reply-item-${targetReviewId}`);
            if (targetEl) {
              targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
              targetEl.style.transition = "box-shadow 0.5s, background-color 0.5s";
              targetEl.style.boxShadow = "0 0 0 4px rgba(212, 175, 55, 0.6)";
              targetEl.style.backgroundColor = "rgba(212, 175, 55, 0.15)";

              setTimeout(() => {
                targetEl.style.boxShadow = "";
                targetEl.style.backgroundColor = "";
              }, 3000);
            }
          }, 600);
        }, 300);
      }
    }
  }, [replies, targetReviewId, reviewId]);

  const fetchReplies = useCallback(
      async (targetPage: number = 0, targetSort: string = sort) => {
        setLoading(true);
        try {
          const res = await fetch(
              `/api/review/${reviewId}/replies?page=${targetPage}&size=20&sort=${targetSort}`,
              { credentials: "same-origin" }
          );
          const result = await res.json();
          if (result.success) {
            setReplies(result.data.replies);
            setTotalPages(result.data.totalPages);
            setPage(targetPage);
            setSort(targetSort);
            if (result.data.validMentionedUsers) {
              setLocalValidMentions((prev) => {
                const merged = new Set([...(prev || []), ...result.data.validMentionedUsers]);
                return Array.from(merged);
              });
            }
          }
        } catch (err) {
          console.error(err);
        } finally {
          setLoading(false);
        }
      },
      [reviewId, sort]
  );

  const handleSubmitReply = async () => {
    if (!replyText.trim()) {
      alert("請輸入內容");
      return;
    }
    try {
      const res = await fetch("/api/review/submit", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "same-origin",
        body: JSON.stringify({
          productId: productId,
          content: replyText,
          parentId: reviewId,
          replyToUser: replyToUser || null,
        }),
      });
      const result = await res.json();
      if (result.success) {
        setReplyText("");
        setReplyToUser("");
        setReplyParentId(null);
        setShowReplyForm(false);
        if (onReplyCountChange) onReplyCountChange(reviewId, 1);
        if (result.data && page === 0 && isOpen) {
          setReplies((prev) => [result.data, ...prev]);
        } else if (isOpen) {
          await fetchReplies(0, "newest");
        }
      } else {
        if (result.message === "BLACKLISTED") {
          if (window.showBlacklistedModal) {
            window.showBlacklistedModal();
          }
          return;
        }
        if (result.message === "GLOBAL_BAN" && result.data && result.data.banned) {
          if (window.showGlobalBanModal) {
            window.showGlobalBanModal(currentUsername, result.data.expiresAt);
          }
          return;
        }
        alert(result.message || "提交失敗");
      }
    } catch (error) {
      console.error("提交回覆失敗:", error);
    }
  };

  const handleDeleteReply = async (replyId: number) => {
    if (isAdmin) {
      setDeletingReplyId(replyId);
      setShowDeleteModal(true);
      setDeleteReason("inappropriate");
      setCustomReason("");
    } else {
      if (typeof window.openDeleteModal === "function") {
        window.openDeleteModal("確定刪除這條回覆嗎？", async () => {
          await executeDeleteReply(replyId);
        });
      } else {
        if (window.confirm("確定刪除這條回覆嗎？")) {
          await executeDeleteReply(replyId);
        }
      }
    }
  };

  const executeDeleteReply = async (replyId: number, reason: string | null = null) => {
    try {
      const deleteBody: Record<string, any> = {};
      if (isAdmin && reason) {
        deleteBody.deleteReason = reason;
      }

      const res = await fetch(`/api/review/${replyId}`, {
        method: "DELETE",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: Object.keys(deleteBody).length > 0 ? JSON.stringify(deleteBody) : undefined,
      });

      const result = await res.json();
      if (result.success) {
        setReplies((prev) => prev.filter((r) => r.id !== replyId));
        if (onReplyCountChange) onReplyCountChange(reviewId, -1);
        if (window.showNotification) {
          window.showNotification("✅ 回覆已刪除");
        }
      } else {
        if (window.showNotification) {
          window.showNotification("❌ " + (result.message || "刪除失敗"), true);
        } else {
          alert(result.message || "刪除失敗");
        }
      }
    } catch (error) {
      console.error("刪除失敗:", error);
      if (window.showNotification) {
        window.showNotification("❌ 網絡錯誤", true);
      } else {
        alert("網絡錯誤");
      }
    }
  };

  const confirmDeleteReply = () => {
    let finalReason = "";
    if (deleteReason === "custom") {
      if (!customReason.trim()) {
        notify("❌ 請輸入自定義原因", true);
        return;
      }
      finalReason = customReason;
    } else {
      switch (deleteReason) {
        case "inappropriate":
          finalReason = "該內容不符合社區規範，因此被移除。";
          break;
        case "ads":
          finalReason = "檢測到廣告或推廣信息，因此被移除。";
          break;
        case "irrelevant":
          finalReason = "該評論與商品無關，因此被移除。";
          break;
        default:
          finalReason = "該內容不符合社區規範，因此被移除。";
      }
    }

    setShowDeleteModal(false);
    if (deletingReplyId !== null) {
      executeDeleteReply(deletingReplyId, finalReason);
    }
    setDeletingReplyId(null);
  };

  const handleReaction = async (replyId: number, isDislike: boolean = false) => {
    if (!currentUsername || currentUsername === "anonymousUser") {
      if (typeof window.showLoginRequiredModal === "function") {
        window.showLoginRequiredModal();
      }
      return;
    }

    const api = isDislike ? `/api/review/${replyId}/dislike` : `/api/review/${replyId}/like`;

    try {
      const res = await fetch(api, {
        method: "POST",
        credentials: "same-origin",
      });
      const result = await res.json();

      if (result.success) {
        setReplies((prev) =>
            prev.map((r) =>
                r.id === replyId
                    ? {
                      ...r,
                      likeCount: result.likeCount,
                      dislikeCount: result.dislikeCount,
                      isLikedByMe: result.liked,
                      isDislikedByMe: result.disliked,
                    }
                    : r
            )
        );
      } else {
        if (result.message === "BLACKLISTED" || result.blacklisted) {
          if (window.showBlacklistedModal) {
            window.showBlacklistedModal();
          }
          return;
        }

        if (result.message && (result.message.includes("1分鐘內操作達到") || result.message.includes("限制"))) {
          notify("❌ " + result.message, true);
          return;
        }

        console.error("點贊/踩失敗:", result.message);
      }
    } catch (err) {
      console.error("網絡請求錯誤:", err);
      notify("網絡錯誤", true);
    }
  };

  const confirmBlock = async (durationMinutes: number) => {
    if (isAdmin && durationMinutes) {
      let totalMinutes = 0;
      if (blockUnit === "day") {
        totalMinutes = durationMinutes * 24 * 60;
      } else if (blockUnit === "week") {
        totalMinutes = durationMinutes * 7 * 24 * 60;
      } else if (blockUnit === "month") {
        totalMinutes = durationMinutes * 30 * 24 * 60;
      }
      const params = new URLSearchParams();
      params.append("durationMinutes", totalMinutes.toString());
      params.append("reason", "管理員禁言");
      if (blockReviewId) params.append("reviewId", blockReviewId.toString());
      if (blockReviewContent) params.append("reviewContent", blockReviewContent);

      try {
        const response = await fetch(`/api/user/${targetBlockUser}/toggle-block?${params}`, {
          method: "POST",
          credentials: "same-origin",
        });
        const result = await response.json();

        if (result.alreadyBanned) {
          const modal = document.getElementById("adminPermissionModal");
          if (modal) {
            modal.style.display = "flex";
            document.body.style.overflow = "hidden";
          }
          return;
        }

        if (response.ok) {
          notify(`✅ 已全局禁言用戶 ${blockValue} ${blockUnit === "day" ? "天" : blockUnit === "week" ? "週" : "月"}`);
          await onBlockUser(targetBlockUser, true);
        } else {
          notify("❌ " + result.message, true);
        }
      } catch (error) {
        notify("❌ 網絡錯誤", true);
      }
    }
    setShowBlockModal(false);
  };

  const handleConfirmBlock = () => {
    const val = parseInt(blockValue.toString(), 10);
    if (!val || val <= 0) {
      notify("❌ 請輸入有效的數字", true);
      return;
    }
    confirmBlock(val);
  };

  const handleUnblock = async (targetUsername: string) => {
    if (isAdmin) {
      const modal = document.getElementById("adminPermissionModal");
      if (modal) {
        modal.style.display = "flex";
        document.body.style.overflow = "hidden";
      }
      return;
    }

    if (typeof window.openUnblockModal === "function") {
      window.openUnblockModal(targetUsername, async (username: string) => {
        const result = await onBlockUser(username, false);
        if (result.success) {
          notify("✅ 已解除禁言");
        } else {
          notify("❌ " + (result.message || ""), true);
        }
      });
    } else {
      if (!window.confirm("確定要解除禁言嗎？")) return;
      const result = await onBlockUser(targetUsername, false);
      if (result.success) {
        notify("✅ 已解除禁言");
      } else {
        notify("❌ " + (result.message || ""), true);
      }
    }
  };

  const handleBlockClick = (targetUsername: string, reviewIdParam: number | null = null, reviewContentParam: string = "") => {
    setTargetBlockUser(targetUsername);
    setBlockReviewId(reviewIdParam);
    setBlockReviewContent(reviewContentParam);

    if (isAdmin) {
      setShowBlockModal(true);
    } else {
      onBlockUser(targetUsername, true).then((result) => {
        if (result.success) {
          notify("✅ 已禁言該用戶，雙方將無法互相回復");
        } else {
          notify("❌ " + (result.message || ""), true);
        }
      });
    }
  };

  const handleBlacklist = (targetUsername: string, reviewIdParam: number, reviewContentParam: string) => {
    if (typeof window.openBlacklistConfirmModal === "function") {
      window.openBlacklistConfirmModal(targetUsername, reviewIdParam, reviewContentParam);
    } else {
      notify("❌ 系統彈窗組件未加載，請刷新頁面後重試", true);
    }
  };

  const formatDate = (dateStr: string): string => {
    if (!dateStr) return "";
    const d = new Date(dateStr);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  };

  const renderContentWithMentions = (content: string, validUsers: string[]): React.ReactNode => {
    if (!content || !validUsers || validUsers.length === 0) {
      return content;
    }

    const sortedUsers = [...validUsers].sort((a, b) => b.length - a.length);
    const escapedUsers = sortedUsers.map((u) => u.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
    const regex = new RegExp(`@(${escapedUsers.join("|")})`, "g");
    const parts = content.split(regex);

    return parts.map((part, index) => {
      if (index % 2 === 1 && sortedUsers.includes(part)) {
        return (
            <span
                key={index}
                style={{
                  color: "#0d6efd",
                  fontWeight: 600,
                  cursor: "pointer",
                  backgroundColor: "rgba(13, 110, 253, 0.08)",
                  borderRadius: "3px",
                  padding: "0 2px",
                }}
                onClick={() => {
                  window.location.href = `/user/${encodeURIComponent(part)}`;
                }}
            >
            @{part}
          </span>
        );
      }
      return part;
    });
  };

  return (
      <div className="reply-section-wrapper">
        {/* 工具欄 */}
        {(initialReplyCount > 0 || isOpen) && (
            <div className="reply-toolbar">
              <div className="reply-toolbar-left">
                {initialReplyCount > 0 && !isOpen && (
                    <button
                        className="reply-btn"
                        onClick={() => {
                          setIsOpen(true);
                          fetchReplies(0, sort);
                        }}
                    >
                      <i className="fas fa-comments"></i> 查看 {initialReplyCount} 條回覆
                    </button>
                )}
                {isOpen && (
                    <button className="reply-btn" onClick={() => setIsOpen(false)}>
                      <i className="fas fa-chevron-up"></i> 收起
                    </button>
                )}
              </div>
              {isOpen && (
                  <div className="reply-toolbar-right">
                    <select
                        onChange={(e) => fetchReplies(0, e.target.value)}
                        value={sort}
                        className="reply-sort-select"
                    >
                      <option value="newest">最新發佈</option>
                      <option value="oldest">最早發佈</option>
                      <option value="popular">最多點贊</option>
                    </select>
                  </div>
              )}
            </div>
        )}

        {/* 列表區域 */}
        {isOpen && (
            <div className="reply-list-container">
              {showReplyForm && replyParentId === null && (
                  <div className="reply-form active" style={{ marginBottom: "1rem" }}>
                    <label>回復 @{replyToUser}：</label>
                    <textarea
                        value={replyText}
                        onChange={(e) => setReplyText(e.target.value)}
                        placeholder="輸入回復內容..."
                        maxLength={500}
                        autoFocus
                    />
                    <div className="form-actions">
                      <button
                          className="btn-cancel-reply"
                          onClick={() => {
                            setShowReplyForm(false);
                            setReplyParentId(null);
                          }}
                      >
                        取消
                      </button>
                      <button className="btn-submit-reply" onClick={handleSubmitReply}>
                        提交
                      </button>
                    </div>
                  </div>
              )}

              {loading ? (
                  <div className="reply-loading">
                    <i className="fas fa-spinner fa-spin"></i> 加載中...
                  </div>
              ) : (
                  <div className="reply-items">
                    {replies.map((reply) => (
                        <div key={reply.id} id={`reply-item-${reply.id}`}>
                          <div className="reply-item">
                            <div className="reply-header">
                              <div className="reply-user">
                                <strong
                                    style={{ cursor: "pointer" }}
                                    onClick={() => (window.location.href = `/user/${encodeURIComponent(reply.customer.username)}`)}
                                    title={`查看 ${reply.customer.username} 的主頁`}
                                >
                                  {reply.customer.username}
                                </strong>
                                {reply.customer.username === currentUsername && (
                                    <span className="reply-badge">我</span>
                                )}
                                {reply.replyToUser && (
                                    <span
                                        style={{ cursor: "pointer", color: "inherit" }}
                                        onClick={() => {
                                          if (reply.replyToUser) {
                                            window.location.href = `/user/${encodeURIComponent(reply.replyToUser)}`;
                                          }
                                        }}
                                        title={`查看 ${reply.replyToUser} 的主頁`}
                                    >
    @{reply.replyToUser}
  </span>
                                )}
                              </div>
                              <div className="reply-date">{formatDate(reply.createdAt)}</div>
                            </div>
                            <div className="reply-content">
                              {renderContentWithMentions(reply.content, localValidMentions)}
                            </div>
                            <div className="reply-actions">
                              <button
                                  className={`reply-action-btn btn-like ${reply.isLikedByMe ? "active" : ""}`}
                                  onClick={() => handleReaction(reply.id)}
                              >
                                <i className={reply.isLikedByMe ? "fas fa-thumbs-up" : "far fa-thumbs-up"}></i>{" "}
                                {reply.likeCount || 0}
                              </button>

                              <button
                                  className={`reply-action-btn btn-dislike ${reply.isDislikedByMe ? "active" : ""}`}
                                  onClick={() => handleReaction(reply.id, true)}
                              >
                                <i className={reply.isDislikedByMe ? "fas fa-thumbs-down" : "far fa-thumbs-down"}></i>{" "}
                                {reply.dislikeCount || 0}
                              </button>

                              <button
                                  className="reply-action-btn"
                                  onClick={async () => {
                                    if (!currentUsername || currentUsername === "anonymousUser") {
                                      if (typeof window.showLoginRequiredModal === "function") {
                                        window.showLoginRequiredModal();
                                      }
                                      return;
                                    }
                                    const targetUsername = reply.customer.username;
                                    if (targetUsername !== currentUsername) {
                                      try {
                                        const res = await fetch(`/api/user/can-reply/${targetUsername}`, { credentials: "same-origin" });
                                        const data = await res.json();
                                        if (data.success && !data.canReply) {
                                          if (window.showBlockedModal) {
                                            window.showBlockedModal("您已經被對方禁言，無法回復！！！");
                                          } else {
                                            alert("您已經被對方禁言，無法回復！！！");
                                          }
                                          return;
                                        }
                                      } catch (e) {
                                        console.error("檢查禁言狀態失敗", e);
                                      }
                                    }
                                    setReplyParentId(reply.id);
                                    setReplyToUser(reply.customer.username);
                                    setShowReplyForm(true);
                                  }}
                              >
                                <i className="fas fa-reply"></i> 回復
                              </button>

                              {(reply.customer.username === currentUsername || isAdmin) && (
                                  <button
                                      className="reply-action-btn delete"
                                      onClick={() => handleDeleteReply(reply.id)}
                                  >
                                    <i className="fas fa-trash-alt"></i> 刪除
                                  </button>
                              )}

                              {currentUsername && currentUsername !== "anonymousUser" && reply.customer.username !== currentUsername && (
                                  isAdmin ? (
                                      <button
                                          className="reply-action-btn btn-ban"
                                          onClick={() => handleBlockClick(reply.customer.username, reply.id, reply.content)}
                                          title="全局禁言"
                                      >
                                        <i className="fas fa-ban"></i> 禁言
                                      </button>
                                  ) : blockedUsers && blockedUsers.includes(reply.customer.username) ? (
                                      <button
                                          className="reply-action-btn btn-unblock"
                                          onClick={() => handleUnblock(reply.customer.username)}
                                          title="解除禁言"
                                      >
                                        <i className="fas fa-unlock"></i> 解除禁言
                                      </button>
                                  ) : (
                                      <button
                                          className="reply-action-btn btn-ban"
                                          onClick={() => handleBlockClick(reply.customer.username)}
                                          title="雙向禁言"
                                      >
                                        <i className="fas fa-ban"></i> 禁言
                                      </button>
                                  )
                              )}

                              {currentUsername && currentUsername !== "anonymousUser" && reply.customer.username !== currentUsername && !isAdmin && (
                                  <button
                                      className="reply-action-btn btn-report"
                                      onClick={() => {
                                        if (typeof window.openReportModal === "function") {
                                          window.openReportModal(reply.id, reply.customer.username, "REVIEW", reply.content);
                                        }
                                      }}
                                      title="舉報不當內容"
                                  >
                                    <i className="fas fa-flag"></i> 舉報
                                  </button>
                              )}
                              {currentUsername && isAdmin && (
                                  <button
                                      className="reply-action-btn btn-blacklist"
                                      onClick={() => handleBlacklist(reply.customer.username, reply.id, reply.content)}//TS2304: Cannot find name handleBlacklist
                                      title="永久拉黑該用戶"
                                  >
                                    <i className="fas fa-user-slash"></i> 永久拉黑
                                  </button>
                              )}
                            </div>
                          </div>

                          {showReplyForm && replyParentId === reply.id && (
                              <div className="reply-form active" style={{ marginLeft: "2rem", marginTop: "0.5rem" }}>
                                <label>回復 @{replyToUser}：</label>
                                <textarea
                                    value={replyText}
                                    onChange={(e) => setReplyText(e.target.value)}
                                    placeholder={`回復 @${replyToUser}...`}
                                    maxLength={500}
                                    autoFocus
                                />
                                <div className="form-actions">
                                  <button
                                      className="btn-cancel-reply"
                                      onClick={() => {
                                        setShowReplyForm(false);
                                        setReplyParentId(null);
                                      }}
                                  >
                                    取消
                                  </button>
                                  <button className="btn-submit-reply" onClick={handleSubmitReply}>
                                    提交
                                  </button>
                                </div>
                              </div>
                          )}
                        </div>
                    ))}
                  </div>
              )}

              {totalPages > 1 && (
                  <div className="reply-pagination">
                    {page > 0 && (
                        <button className="btn btn-outline" onClick={() => fetchReplies(page - 1)}>
                          上一頁
                        </button>
                    )}
                    {Array.from({ length: totalPages }, (_, i) => (
                        <button
                            key={i}
                            className={`btn ${i === page ? "btn-gold" : "btn-outline"}`}
                            onClick={() => fetchReplies(i)}
                        >
                          {i + 1}
                        </button>
                    ))}
                    {page < totalPages - 1 && (
                        <button className="btn btn-outline" onClick={() => fetchReplies(page + 1)}>
                          下一頁
                        </button>
                    )}
                  </div>
              )}
            </div>
        )}

        {!isOpen && showReplyForm && replyParentId === null && (
            <div className="reply-form active" style={{ marginTop: "1rem" }}>
              <label>回復 @{replyToUser}：</label>
              <textarea
                  value={replyText}
                  onChange={(e) => setReplyText(e.target.value)}
                  placeholder="輸入回復內容..."
                  maxLength={500}
                  autoFocus
              />
              <div className="form-actions">
                <button
                    className="btn-cancel-reply"
                    onClick={() => {
                      setShowReplyForm(false);
                      setReplyParentId(null);
                    }}
                >
                  取消
                </button>
                <button className="btn-submit-reply" onClick={handleSubmitReply}>
                  提交
                </button>
              </div>
            </div>
        )}

        {/* 管理員禁言時長選擇模態框 */}
        {showBlockModal && (
            <div
                className="modal-overlay"
                onClick={() => setShowBlockModal(false)}
                style={{
                  position: "fixed",
                  top: 0,
                  left: 0,
                  right: 0,
                  bottom: 0,
                  backgroundColor: "rgba(0,0,0,0.5)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  zIndex: 9999,
                }}
            >
              <div
                  className="modal-box"
                  onClick={(e) => e.stopPropagation()}
                  style={{
                    background: "white",
                    padding: "2rem",
                    borderRadius: "12px",
                    width: "90%",
                    maxWidth: "500px",
                    boxShadow: "0 10px 25px rgba(0,0,0,0.2)",
                  }}
              >
                <h3 style={{ marginBottom: "1rem", color: "var(--primary)" }}>
                  <i className="fas fa-gavel" style={{ color: "var(--accent)", marginRight: "0.5rem" }}></i>
                  管理員禁言
                </h3>
                <p style={{ marginBottom: "1rem", color: "var(--gray)" }}>
                  請輸入禁言時長，用戶 <strong>{targetBlockUser}</strong> 在期間內將無法在任何評論區回覆：
                </p>
                <div style={{ display: "flex", gap: "1rem", alignItems: "center", margin: "1.5rem 0" }}>
                  <input
                      type="number"
                      min={1}
                      value={blockValue}
                      onChange={(e) => setBlockValue(Number(e.target.value))}
                      placeholder="請輸入數字"
                      style={{ flex: 1, padding: "0.5rem", border: "1px solid #ddd", borderRadius: "4px" }}
                  />
                  <select
                      value={blockUnit}
                      onChange={(e) => setBlockUnit(e.target.value as "day" | "week" | "month")}
                      style={{ padding: "0.5rem", border: "1px solid #ddd", borderRadius: "4px", minWidth: "80px" }}
                  >
                    <option value="day">天</option>
                    <option value="week">週</option>
                    <option value="month">月</option>
                  </select>
                </div>
                <div style={{ display: "flex", gap: "1rem", justifyContent: "flex-end", marginTop: "1.5rem" }}>
                  <button
                      onClick={() => setShowBlockModal(false)}
                      style={{ padding: "0.5rem 1rem", background: "#e9ecef", border: "none", borderRadius: "4px", cursor: "pointer" }}
                  >
                    取消
                  </button>
                  <button
                      onClick={handleConfirmBlock}
                      style={{ padding: "0.5rem 1rem", background: "var(--accent)", color: "white", border: "none", borderRadius: "4px", cursor: "pointer" }}
                  >
                    確認禁言
                  </button>
                </div>
              </div>
            </div>
        )}

        {/* 管理員刪除回覆確認模態框 */}
        {showDeleteModal && (
            <div
                className="modal-overlay"
                onClick={() => setShowDeleteModal(false)}
                style={{
                  position: "fixed",
                  top: 0,
                  left: 0,
                  right: 0,
                  bottom: 0,
                  backgroundColor: "rgba(0,0,0,0.5)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  zIndex: 9999,
                }}
            >
              <div
                  className="modal-box"
                  onClick={(e) => e.stopPropagation()}
                  style={{
                    background: "white",
                    padding: "2rem",
                    borderRadius: "12px",
                    width: "90%",
                    maxWidth: "500px",
                    boxShadow: "0 10px 25px rgba(0,0,0,0.2)",
                  }}
              >
                <h3 style={{ marginBottom: "1rem", color: "var(--primary)" }}>
                  <i className="fas fa-exclamation-triangle" style={{ color: "var(--accent)", marginRight: "0.5rem" }}></i>
                  刪除回覆確認
                </h3>
                <p style={{ marginBottom: "1rem", color: "var(--gray)" }}>
                  請選擇刪除原因，這將發送通知給用戶{" "}
                  <strong>
                    {replies.find((r) => r.id === deletingReplyId)?.customer?.username || "該用戶"}
                  </strong>：
                </p>

                <div style={{ marginBottom: "1rem" }}>
                  <label style={{ display: "block", marginBottom: "0.5rem", cursor: "pointer" }}>
                    <input
                        type="radio"
                        name="deleteReason"
                        value="inappropriate"
                        checked={deleteReason === "inappropriate"}
                        onChange={(e) => setDeleteReason(e.target.value)}
                        style={{ marginRight: "0.5rem" }}
                    />
                    內容不合規 (默認)
                  </label>
                  <label style={{ display: "block", marginBottom: "0.5rem", cursor: "pointer" }}>
                    <input
                        type="radio"
                        name="deleteReason"
                        value="ads"
                        checked={deleteReason === "ads"}
                        onChange={(e) => setDeleteReason(e.target.value)}
                        style={{ marginRight: "0.5rem" }}
                    />
                    廣告或推廣信息
                  </label>
                  <label style={{ display: "block", marginBottom: "0.5rem", cursor: "pointer" }}>
                    <input
                        type="radio"
                        name="deleteReason"
                        value="irrelevant"
                        checked={deleteReason === "irrelevant"}
                        onChange={(e) => setDeleteReason(e.target.value)}
                        style={{ marginRight: "0.5rem" }}
                    />
                    與商品無關
                  </label>
                  <label style={{ display: "block", marginBottom: "0.5rem", cursor: "pointer" }}>
                    <input
                        type="radio"
                        name="deleteReason"
                        value="custom"
                        checked={deleteReason === "custom"}
                        onChange={(e) => setDeleteReason(e.target.value)}
                        style={{ marginRight: "0.5rem" }}
                    />
                    自定義原因
                  </label>
                </div>

                {deleteReason === "custom" && (
                    <textarea
                        style={{
                          width: "100%",
                          minHeight: "80px",
                          padding: "0.5rem",
                          borderRadius: "4px",
                          border: "1px solid #ddd",
                          fontFamily: "inherit",
                          marginBottom: "1rem",
                        }}
                        placeholder="請輸入具體的刪除原因..."
                        value={customReason}
                        onChange={(e) => setCustomReason(e.target.value)}
                    />
                )}

                <div style={{ display: "flex", gap: "1rem", justifyContent: "flex-end", marginTop: "1.5rem" }}>
                  <button
                      onClick={() => setShowDeleteModal(false)}
                      style={{ padding: "0.5rem 1rem", background: "#e9ecef", border: "none", borderRadius: "4px", cursor: "pointer" }}
                  >
                    取消
                  </button>
                  <button
                      onClick={confirmDeleteReply}
                      style={{ padding: "0.5rem 1rem", background: "var(--accent)", color: "white", border: "none", borderRadius: "4px", cursor: "pointer" }}
                  >
                    確認刪除
                  </button>
                </div>
              </div>
            </div>
        )}
      </div>
  );
};

export default ReplySection;