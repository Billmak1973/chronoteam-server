import React, { useState, useCallback, useRef, useEffect } from "react";
import ReplySection from "./ReplySection";

// ==========================================
// 1. 全局 Window 接口擴展
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
    confirmRepeatBan?: () => void;
    openBlacklistConfirmModal?: (targetUsername: string, reviewId: number, reviewContent: string) => void;
    refreshReviews?: () => void;
  }
}

// ==========================================
// 2. 數據模型接口定義
// ==========================================
export interface ReviewCustomer {
  username: string;
}

export interface Review {
  id: number;
  content: string;
  formattedContent?: string | null;
  isFormatted?: boolean;
  rating?: number;
  createdAt: string;
  likeCount: number;
  dislikeCount: number;
  isLikedByMe: boolean;
  isDislikedByMe: boolean;
  replyCount: number;
  pinned: boolean;
  customer: ReviewCustomer;
}

export interface ReviewCardProps {
  review: Review;
  currentUsername: string;
  isAdmin: boolean;
  productId: number;
  onReplyCountChange: (reviewId: number, delta: number) => void;
  onReviewDeleted: (reviewId: number) => void;
  onReviewUpdated: (updatedReview: Review) => void;
  blockedUsers: string[];
  onBlockUser: (targetUsername: string, shouldBlock: boolean) => Promise<{ success: boolean; message?: string }>;
  targetReviewId: string | null;
  validMentionedUsers: string[];
}

// ==========================================
// 3. 組件主體
// ==========================================
const ReviewCard: React.FC<ReviewCardProps> = ({
                                                 review,
                                                 currentUsername,
                                                 isAdmin,
                                                 productId,
                                                 onReplyCountChange,
                                                 onReviewDeleted,
                                                 onReviewUpdated,
                                                 blockedUsers,
                                                 onBlockUser,
                                                 targetReviewId,
                                                 validMentionedUsers,
                                               }) => {
  // 輔助函數：安全調用全局函數
  const notify = (message: string, isError: boolean = false): void => {
    if (typeof window.showNotification === "function") {
      window.showNotification(message, isError);
    } else {
      alert(message);
    }
  };

  // --- 現有狀態 ---
  const [likeCount, setLikeCount] = useState<number>(review.likeCount || 0);
  const [dislikeCount, setDislikeCount] = useState<number>(review.dislikeCount || 0);
  const [isEditing, setIsEditing] = useState<boolean>(false);
  const [editContent, setEditContent] = useState<string>(review.content);
  const [editMode, setEditMode] = useState<"text" | "rich">("text");
  const editorRef = useRef<HTMLDivElement>(null);
  const [isLiked, setIsLiked] = useState<boolean>(review.isLikedByMe || false);
  const [isDisliked, setIsDisliked] = useState<boolean>(review.isDislikedByMe || false);
  const [showReplyForm, setShowReplyForm] = useState<boolean>(false);
  const [showDeleteModal, setShowDeleteModal] = useState<boolean>(false);
  const [deleteReason, setDeleteReason] = useState<string>("");
  const [customReason, setCustomReason] = useState<string>("");
  const [showBlockModal, setShowBlockModal] = useState<boolean>(false);
  const [blockValue, setBlockValue] = useState<number>(1);
  const [blockUnit, setBlockUnit] = useState<"day" | "week" | "month">("day");
  const [targetBlockUser, setTargetBlockUser] = useState<string>("");

  const isAuthorBlocked = blockedUsers && blockedUsers.includes(review.customer.username);

  const hasInteractions = useCallback((): boolean => {
    if (likeCount > 0 || dislikeCount > 0) {
      return true;
    }
    if (review.replyCount > 0) {
      return true;
    }
    return false;
  }, [likeCount, dislikeCount, review.replyCount]);

  useEffect(() => {
    if (targetReviewId && review.id.toString() === targetReviewId) {
      setTimeout(() => {
        const targetEl = document.getElementById(`review-card-${review.id}`);
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
      }, 500);
    }
  }, [targetReviewId, review.id]);

  const handleLike = async (): Promise<void> => {
    if (!currentUsername || currentUsername === "anonymousUser") {
      if (typeof window.showLoginRequiredModal === "function") {
        window.showLoginRequiredModal();
      }
      return;
    }

    try {
      const res = await fetch(`/api/review/${review.id}/like`, {
        method: "POST",
        credentials: "same-origin",
      });
      const result = await res.json();

      if (result.success) {
        setLikeCount(result.likeCount);
        setDislikeCount(result.dislikeCount);
        setIsLiked(result.liked);
        setIsDisliked(result.disliked);
      } else {
        if (result.message === "BLACKLISTED" || result.blacklisted) {
          if (window.showBlacklistedModal) {
            window.showBlacklistedModal();
          }
          return;
        }

        if (
            result.message &&
            (result.message.includes("1分鐘內操作達到") ||
                result.message.includes("限制"))
        ) {
          notify("❌ " + result.message, true);
          return;
        }

        notify(result.message || "點贊失敗", true);
      }
    } catch (error) {
      console.error("點贊網絡錯誤:", error);
      notify("網絡錯誤", true);
    }
  };

  const handleDislike = async (): Promise<void> => {
    if (!currentUsername || currentUsername === "anonymousUser") {
      if (typeof window.showLoginRequiredModal === "function") {
        window.showLoginRequiredModal();
      }
      return;
    }

    try {
      const res = await fetch(`/api/review/${review.id}/dislike`, {
        method: "POST",
        credentials: "same-origin",
      });
      const result = await res.json();

      if (result.success) {
        setLikeCount(result.likeCount);
        setDislikeCount(result.dislikeCount);
        setIsLiked(result.liked);
        setIsDisliked(result.disliked);
      } else {
        if (result.message === "BLACKLISTED" || result.blacklisted) {
          if (window.showBlacklistedModal) {
            window.showBlacklistedModal();
          }
          return;
        }

        if (
            result.message &&
            (result.message.includes("1分鐘內操作達到") ||
                result.message.includes("限制"))
        ) {
          notify("❌ " + result.message, true);
          return;
        }

        notify(result.message || "踩失敗", true);
      }
    } catch (error) {
      console.error("踩網絡錯誤:", error);
      notify("網絡錯誤", true);
    }
  };

  const handleDeleteAction = async (reasonText: string | null = null): Promise<void> => {
    try {
      const body: Record<string, any> = {};
      if (isAdmin && reasonText) {
        body.deleteReason = reasonText;
      }
      const res = await fetch(`/api/review/${review.id}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        credentials: "same-origin",
        body: Object.keys(body).length > 0 ? JSON.stringify(body) : undefined,
      });
      const result = await res.json();
      if (res.ok && result.success) {
        setShowDeleteModal(false);
        if (typeof window.showNotification === "function") {
          window.showNotification("✅ 評論已刪除，頁面將刷新...");
        }
        setTimeout(() => {
          window.location.reload();
        }, 1000);
      } else {
        alert(result.message || "刪除失敗");
      }
    } catch (error) {
      console.error("網絡錯誤:", error);
      alert("網絡錯誤，請稍後重試");
    }
  };

  const handleDeleteClick = (): void => {
    if (isAdmin) {
      setShowDeleteModal(true);
      setDeleteReason("inappropriate");
      setCustomReason("");
    } else {
      if (window.openDeleteModal) {
        window.openDeleteModal(
            "確定刪除這條評論嗎？此操作無法恢復！",
            async () => {
              try {
                const res = await fetch(`/api/review/${review.id}`, {
                  method: "DELETE",
                  credentials: "same-origin",
                });
                const result = await res.json();
                if (result.success) {
                  if (window.showNotification) {
                    window.showNotification("✅ 評論已刪除，頁面將刷新...");
                  }
                  setTimeout(() => {
                    window.location.reload();
                  }, 1000);
                } else {
                  alert(result.message || "刪除失敗");
                }
              } catch (error) {
                console.error("刪除失敗:", error);
                alert("網絡錯誤");
              }
            },
        );
      }
    }
  };

  const confirmDeleteWithReason = (): void => {
    let finalReason = "";
    if (deleteReason === "custom") {
      if (!customReason.trim()) {
        alert("請輸入自定義原因");
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
    handleDeleteAction(finalReason);
  };

  const handleEditClick = (): void => {
    setEditMode("rich");
    setEditContent(review.formattedContent || review.content);
    setIsEditing(true);
  };

  const handleSaveEdit = async (): Promise<void> => {
    if (!editContent.trim()) {
      alert("評論內容不能為空");
      return;
    }
    try {
      const res = await fetch(`/api/review/${review.id}/update`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "same-origin",
        body: JSON.stringify({
          content: editContent,
          isFormatted: editMode === "rich",
        }),
      });
      const result = await res.json();

      if (result.success) {
        setLikeCount(review.likeCount);
        setDislikeCount(review.dislikeCount);
        onReviewUpdated({
          ...review,
          content: editContent,
          formattedContent: editMode === "rich" ? editContent : null,
          isFormatted: editMode === "rich",
        });
        setIsEditing(false);

        if (typeof window.refreshReviews === "function") {
          window.refreshReviews();
        }
      } else {
        if (
            result.message === "GLOBAL_BAN" &&
            result.data &&
            result.data.banned
        ) {
          if (window.showGlobalBanModal) {
            window.showGlobalBanModal(currentUsername, result.data.expiresAt);
          }
          return;
        }
        if (result.message === "BLACKLISTED") {
          if (window.showBlacklistedModal) {
            window.showBlacklistedModal();
          }
          return;
        }
        alert(result.message || "修改失敗");
      }
    } catch (error) {
      console.error("修改評論錯誤:", error);
      alert("網絡錯誤");
    }
  };

  const handlePin = async (): Promise<void> => {
    try {
      const res = await fetch(`/api/review/${review.id}/pin`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ pinned: !review.pinned }),
      });
      const result = await res.json();
      if (result.success) {
        onReviewUpdated({ ...review, pinned: !review.pinned });
      } else {
        alert(result.message || "置頂操作失敗");
      }
    } catch (error) {
      console.error("置頂錯誤:", error);
      alert("網絡錯誤");
    }
  };

  const confirmBlock = async (durationMinutes: number): Promise<void> => {
    const targetUsername = review.customer.username;
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
      params.append("reviewId", review.id.toString());
      params.append("reviewContent", review.content);
      try {
        const response = await fetch(
            `/api/user/${targetUsername}/toggle-block?${params}`,
            { method: "POST", credentials: "same-origin" },
        );
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
          notify(
              `✅ 已全局禁言用戶 ${blockValue} ${blockUnit === "day" ? "天" : blockUnit === "week" ? "週" : "月"}`,
          );
          await onBlockUser(targetUsername, true);
        } else {
          notify("❌ " + result.message, true);
        }
      } catch (error) {
        notify("❌ 網絡錯誤", true);
      }
    } else {
      const result = await onBlockUser(targetUsername, true);
      if (result.success) {
        notify("✅ 已禁言該用戶，雙方將無法互相回復");
      } else {
        notify("❌ " + (result.message || ""), true);
      }
    }
    setShowBlockModal(false);
  };

  const handleConfirmBlock = (): void => {
    const val = parseInt(blockValue.toString(), 10);
    if (!val || val <= 0) {
      notify("❌ 請輸入有效的數字", true);
      return;
    }
    confirmBlock(val);
  };

  const handleUnblock = async (): Promise<void> => {
    const targetUsername = review.customer.username;

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
    return;
  };

  const handleBlockClick = (targetUsername: string): void => {
    setTargetBlockUser(targetUsername);

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

  window.confirmRepeatBan = (): void => {
    if (typeof window.closeAdminPermissionModal === "function") {
      window.closeAdminPermissionModal();
    } else {
      const modal = document.getElementById("adminPermissionModal");
      if (modal) {
        modal.style.display = "none";
        document.body.style.overflow = "";
      }
    }
    setShowBlockModal(true);
  };

  const handleBlacklist = (targetUsername: string, reviewId: number, reviewContent: string): void => {
    if (typeof window.openBlacklistConfirmModal === "function") {
      window.openBlacklistConfirmModal(targetUsername, reviewId, reviewContent);
    } else {
      notify("❌ 系統彈窗組件未加載，請刷新頁面後重試", true);
    }
  };

  // 定義一個名為 renderStars 的函數，它接收一個「可選的」數字參數 rating (評分)，並規定該函數必須返回一個 React 可以渲染的內容 (例如 JSX 標籤或 null)
  const renderStars = (rating?: number): React.ReactNode => {
    if (!rating) return null;
    const full = Math.floor(rating);
    const half = rating - full >= 0.5;
    return (
        <div className="review-rating">
          {[1, 2, 3, 4, 5].map((i) => {
            if (i <= full) return <i key={i} className="fas fa-star"></i>;
            if (i === full + 1 && half)
              return <i key={i} className="fas fa-star-half-alt"></i>;
            return <i key={i} className="far fa-star"></i>;
          })}
        </div>
    );
  };

  // 將 value 的型別改為可選參數 (string | undefined)
  const formatText = (command: string, value?: string): void => {
    // 這樣傳入 undefined 是完全符合 document.execCommand 型別定義的
    document.execCommand(command, false, value);
    if (editorRef.current) {
      setEditContent(editorRef.current.innerHTML);
    }
    editorRef.current?.focus();
  };

  const insertLink = (): void => {
    const url = prompt("請輸入連結地址：", "https://");
    // 在 if (url) 的保護下，TypeScript 會聰明地推斷出 url 的型別是 string (排除了 null)
    if (url) {
      formatText("createLink", url);
    }
  };

  const renderContentWithMentions = (content: string, validUsers: string[]): React.ReactNode => {
    if (!content || !validUsers || validUsers.length === 0) {
      return content;
    }

    const sortedUsers = [...validUsers].sort((a, b) => b.length - a.length);
    const escapedUsers = sortedUsers.map((u) =>
        u.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"),
    );
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

  const toolbarBtnStyle: React.CSSProperties = {
    padding: "0.4rem 0.8rem",
    border: "1px solid #ddd",
    borderRadius: "4px",
    backgroundColor: "#fff",
    cursor: "pointer",
    fontSize: "0.9rem",
    transition: "all 0.2s",
    minWidth: "40px",
  };

  const modalOverlayStyle: React.CSSProperties = {
    position: "fixed",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: "rgba(0, 0, 0, 0.5)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 9999,
  };

  const modalContentStyle: React.CSSProperties = {
    background: "white",
    padding: "2rem",
    borderRadius: "12px",
    width: "90%",
    maxWidth: "500px",
    boxShadow: "0 10px 25px rgba(0,0,0,0.2)",
  };

  return (
      <div
          id={`review-card-${review.id}`}
          className={`review-card ${review.pinned ? "pinned" : ""}`}
      >
        <div className="review-header">
          <div className="reviewer-info">
            <div className="reviewer-name">
              <i className="fas fa-user-circle"></i>
              <span
                  style={{ cursor: "pointer" }}
                  onClick={() =>
                      (window.location.href = `/user/${encodeURIComponent(review.customer.username)}`)
                  }
                  title={`查看 ${review.customer.username} 的主頁`}
              >
              {review.customer.username}
            </span>
              {review.customer.username === currentUsername && (
                  <span className="me-badge">我</span>
              )}
            </div>

            {renderStars(review.rating)}//TS2345: Argument of type number | undefined is not assignable to parameter of type number
            <div className="review-date">
              {new Date(review.createdAt).toLocaleDateString()}
            </div>
          </div>
          <div className="review-reactions">
            <div className="review-reaction-group">
              <button
                  className={`review-action-btn btn-like ${isLiked ? "active" : ""}`}
                  onClick={handleLike}
              >
                <i
                    className={isLiked ? "fas fa-thumbs-up" : "far fa-thumbs-up"}
                ></i>{" "}
                {likeCount}
              </button>
              <button
                  className={`review-action-btn btn-dislike ${isDisliked ? "active" : ""}`}
                  onClick={handleDislike}
              >
                <i
                    className={
                      isDisliked ? "fas fa-thumbs-down" : "far fa-thumbs-down"
                    }
                ></i>{" "}
                {dislikeCount}
              </button>
              <button
                  className="review-action-btn btn-reply"
                  onClick={async () => {
                    if (!currentUsername || currentUsername === "anonymousUser") {
                      if (typeof window.showLoginRequiredModal === "function") {
                        window.showLoginRequiredModal();
                      }
                      return;
                    }
                    const authorUsername = review.customer.username;
                    if (authorUsername !== currentUsername) {
                      try {
                        const res = await fetch(
                            `/api/user/can-reply/${authorUsername}`,
                            { credentials: "same-origin" },
                        );
                        const data = await res.json();
                        if (data.success && !data.canReply) {
                          if (window.showBlockedModal) {
                            window.showBlockedModal(
                                "您已經被對方禁言，無法回復！！！",
                            );
                          } else {
                            alert("您已經被對方禁言，無法回復！！！");
                          }
                          return;
                        }
                      } catch (e) {
                        console.error("檢查禁言狀態失敗", e);
                      }
                    }
                    setShowReplyForm(true);
                  }}
              >
                <i className="fas fa-reply"></i> 回復
              </button>
              {isAdmin && (
                  <button
                      className={`review-action-btn btn-pin-header ${review.pinned ? "active" : ""}`}
                      onClick={handlePin}
                      title={review.pinned ? "取消置頂" : "置頂評論"}
                  >
                    <i className="fas fa-thumbtack"></i>
                    <span className="pin-text">
                  {review.pinned ? "取消置頂" : "置頂"}
                </span>
                  </button>
              )}
            </div>
          </div>
        </div>

        {isEditing ? (
            <div
                className="edit-container"
                style={{
                  border: "2px solid var(--gold)",
                  borderRadius: "8px",
                  padding: "1rem",
                  marginBottom: "1rem",
                  backgroundColor: "#fff",
                }}
            >
              {editMode === "rich" && (
                  <div
                      className="editor-toolbar"
                      style={{
                        display: "flex",
                        gap: "0.5rem",
                        marginBottom: "1rem",
                        padding: "0.5rem",
                        backgroundColor: "#f8f9fa",
                        borderRadius: "6px",
                        flexWrap: "wrap",
                      }}
                  >
                    <button
                        type="button"
                        onClick={() => formatText("bold")}
                        style={toolbarBtnStyle}
                        title="粗體"
                    >
                      <b>B</b>
                    </button>
                    <button
                        type="button"
                        onClick={() => formatText("italic")}
                        style={toolbarBtnStyle}
                        title="斜體"
                    >
                      <i>I</i>
                    </button>
                    <button
                        type="button"
                        onClick={() => formatText("underline")}
                        style={toolbarBtnStyle}
                        title="下劃線"
                    >
                      <u>U</u>
                    </button>
                    <div
                        style={{
                          width: "1px",
                          backgroundColor: "#ddd",
                          margin: "0 0.25rem",
                        }}
                    ></div>
                    <button
                        type="button"
                        onClick={() => formatText("foreColor", "#e94560")}
                        style={{ ...toolbarBtnStyle, color: "#e94560" }}
                        title="紅色字體"
                    >
                      A
                    </button>
                    <button
                        type="button"
                        onClick={() => formatText("hiliteColor", "#fff3cd")}
                        style={{ ...toolbarBtnStyle, backgroundColor: "#fff3cd" }}
                        title="背景色"
                    >
                      A
                    </button>
                    <div
                        style={{
                          width: "1px",
                          backgroundColor: "#ddd",
                          margin: "0 0.25rem",
                        }}
                    ></div>
                    <button
                        type="button"
                        onClick={() => formatText("insertUnorderedList")}
                        style={toolbarBtnStyle}
                        title="列表"
                    >
                      • List
                    </button>
                    <button
                        type="button"
                        onClick={insertLink}
                        style={toolbarBtnStyle}
                        title="插入連結"
                    >
                      🔗
                    </button>
                  </div>
              )}

              {editMode === "rich" ? (
                  <div
                      ref={editorRef}
                      contentEditable
                      dangerouslySetInnerHTML={{ __html: editContent }}
                      onInput={(e: React.FormEvent<HTMLDivElement>) => setEditContent((e.target as HTMLDivElement).innerHTML)}
                      style={{
                        minHeight: "150px",
                        padding: "1rem",
                        border: "1px solid #ddd",
                        borderRadius: "6px",
                        outline: "none",
                        backgroundColor: "#fff",
                      }}
                  />
              ) : (
                  <textarea
                      value={editContent}
                      onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setEditContent(e.target.value)}
                      maxLength={1000}
                      style={{
                        width: "100%",
                        minHeight: "150px",
                        padding: "1rem",
                        border: "1px solid #ddd",
                        borderRadius: "6px",
                        resize: "vertical",
                        fontFamily: "inherit",
                      }}
                  />
              )}

              <div
                  style={{
                    display: "flex",
                    gap: "1rem",
                    justifyContent: "flex-end",
                    marginTop: "1rem",
                  }}
              >
                <button
                    onClick={() => setIsEditing(false)}
                    style={{
                      padding: "0.6rem 1.5rem",
                      border: "none",
                      borderRadius: "6px",
                      backgroundColor: "#e9ecef",
                      color: "#495057",
                      cursor: "pointer",
                      fontWeight: 500,
                    }}
                >
                  取消
                </button>
                <button
                    onClick={handleSaveEdit}
                    style={{
                      padding: "0.6rem 1.5rem",
                      border: "none",
                      borderRadius: "6px",
                      backgroundColor: "var(--gold)",
                      color: "var(--primary)",
                      cursor: "pointer",
                      fontWeight: 600,
                    }}
                >
                  保存修改
                </button>
              </div>
            </div>
        ) : (
            <div className="review-content">
              {review.isFormatted && review.formattedContent ? (
                  <span
                      dangerouslySetInnerHTML={{ __html: review.formattedContent }}
                  />
              ) : (
                  <span>
              {renderContentWithMentions(review.content, validMentionedUsers)}
            </span>
              )}
            </div>
        )}

        <div className="review-actions">
          {review.customer.username === currentUsername && !review.pinned && (
              <button
                  className="btn-edit"
                  onClick={handleEditClick}
                  disabled={hasInteractions()}
                  title={hasInteractions() ? "已有互動（點贊/回覆），無法修改" : ""}
                  style={
                    hasInteractions() ? { opacity: 0.5, cursor: "not-allowed" } : {}
                  }
              >
                <i className="fas fa-edit"></i> 修改
              </button>
          )}

          {((review.customer.username === currentUsername && !review.pinned) ||
              isAdmin) && (
              <button className="btn-delete" onClick={handleDeleteClick}>
                <i className="fas fa-trash-alt"></i> 刪除
              </button>
          )}

          {currentUsername &&
              currentUsername !== "anonymousUser" &&
              review.customer.username !== currentUsername &&
              (isAdmin ? (
                      <button
                          className="btn-ban"
                          onClick={() => handleBlockClick(review.customer.username)}
                          title="全局禁言該用戶"
                      >
                        <i className="fas fa-ban"></i> 禁言
                      </button>
                  ) :
                  blockedUsers && blockedUsers.includes(review.customer.username) ? (
                      <button
                          className="btn-unblock"
                          onClick={handleUnblock}
                          title="解除禁言"
                      >
                        <i className="fas fa-unlock"></i> 解除禁言
                      </button>
                  ) : (
                      <button
                          className="btn-ban"
                          onClick={() => handleBlockClick(review.customer.username)}
                          title="雙向禁言該用戶"
                      >
                        <i className="fas fa-ban"></i> 禁言
                      </button>
                  ))}

          {currentUsername &&
              currentUsername !== "anonymousUser" &&
              review.customer.username !== currentUsername &&
              !isAdmin && (
                  <button
                      className="btn-report"
                      onClick={() => {
                        if (typeof window.openReportModal === "function") {
                          window.openReportModal(
                              review.id,
                              review.customer.username,
                              "REVIEW",
                              review.content,
                          );
                        }
                      }}
                      title="舉報不當內容"
                  >
                    <i className="fas fa-flag"></i> 舉報
                  </button>
              )}

          {isAdmin && (
              <button
                  className="btn-blacklist"
                  onClick={() =>
                      handleBlacklist(
                          review.customer.username,
                          review.id,
                          review.content,
                      )
                  }
                  title="永久拉黑該用戶（加入黑名單）"
              >
                <i className="fas fa-user-slash"></i> 永久拉黑
              </button>
          )}
        </div>

        <ReplySection
            reviewId={review.id}
            initialReplyCount={review.replyCount}
            currentUsername={currentUsername}
            isAdmin={isAdmin}
            productId={productId}
            rootUsername={review.customer.username}
            onReplyCountChange={onReplyCountChange}
            showReplyForm={showReplyForm}
            setShowReplyForm={setShowReplyForm}
            blockedUsers={blockedUsers}
            onBlockUser={onBlockUser}
            targetReviewId={targetReviewId}
            validMentionedUsers={validMentionedUsers}
        />

        {showDeleteModal && (
            <div style={modalOverlayStyle}>
              <div style={modalContentStyle}>
                <h3 style={{ marginBottom: "1rem", color: "var(--primary)" }}>
                  <i
                      className="fas fa-exclamation-triangle"
                      style={{ color: "var(--accent)", marginRight: "0.5rem" }}
                  ></i>
                  刪除評論確認
                </h3>
                <p style={{ marginBottom: "1rem", color: "var(--gray)" }}>
                  請選擇刪除原因，這將發送通知給用戶{" "}
                  <strong>{review.customer.username}</strong>：
                </p>
                <div style={{ marginBottom: "1rem" }}>
                  <label
                      style={{
                        display: "block",
                        marginBottom: "0.5rem",
                        cursor: "pointer",
                      }}
                  >
                    <input
                        type="radio"
                        name="deleteReason"
                        value="inappropriate"
                        checked={deleteReason === "inappropriate"}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setDeleteReason(e.target.value)}
                        style={{ marginRight: "0.5rem" }}
                    />
                    內容不合規 (默認)
                  </label>
                  <label
                      style={{
                        display: "block",
                        marginBottom: "0.5rem",
                        cursor: "pointer",
                      }}
                  >
                    <input
                        type="radio"
                        name="deleteReason"
                        value="ads"
                        checked={deleteReason === "ads"}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setDeleteReason(e.target.value)}
                        style={{ marginRight: "0.5rem" }}
                    />
                    廣告或推廣信息
                  </label>
                  <label
                      style={{
                        display: "block",
                        marginBottom: "0.5rem",
                        cursor: "pointer",
                      }}
                  >
                    <input
                        type="radio"
                        name="deleteReason"
                        value="irrelevant"
                        checked={deleteReason === "irrelevant"}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setDeleteReason(e.target.value)}
                        style={{ marginRight: "0.5rem" }}
                    />
                    與商品無關
                  </label>
                  <label
                      style={{
                        display: "block",
                        marginBottom: "0.5rem",
                        cursor: "pointer",
                      }}
                  >
                    <input
                        type="radio"
                        name="deleteReason"
                        value="custom"
                        checked={deleteReason === "custom"}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setDeleteReason(e.target.value)}
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
                        }}
                        placeholder="請輸入具體的刪除原因..."
                        value={customReason}
                        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setCustomReason(e.target.value)}
                    />
                )}
                <div
                    style={{
                      display: "flex",
                      gap: "1rem",
                      justifyContent: "flex-end",
                      marginTop: "1.5rem",
                    }}
                >
                  <button
                      onClick={() => setShowDeleteModal(false)}
                      style={{
                        padding: "0.5rem 1rem",
                        background: "#e9ecef",
                        border: "none",
                        borderRadius: "4px",
                        cursor: "pointer",
                      }}
                  >
                    取消
                  </button>
                  <button
                      onClick={confirmDeleteWithReason}
                      style={{
                        padding: "0.5rem 1rem",
                        background: "var(--accent)",
                        color: "white",
                        border: "none",
                        borderRadius: "4px",
                        cursor: "pointer",
                      }}
                  >
                    確認刪除
                  </button>
                </div>
              </div>
            </div>
        )}

        {showBlockModal && (
            <div style={modalOverlayStyle} onClick={() => setShowBlockModal(false)}>
              <div style={modalContentStyle} onClick={(e: React.MouseEvent<HTMLDivElement>) => e.stopPropagation()}>
                <h3 style={{ marginBottom: "1rem", color: "var(--primary)" }}>
                  <i
                      className="fas fa-gavel"
                      style={{ color: "var(--accent)", marginRight: "0.5rem" }}
                  ></i>
                  管理員禁言
                </h3>
                <p style={{ marginBottom: "1rem", color: "var(--gray)" }}>
                  請輸入禁言時長，用戶 <strong>{targetBlockUser}</strong>{" "}
                  在期間內將無法在任何評論區回覆：
                </p>
                <div
                    style={{
                      display: "flex",
                      gap: "1rem",
                      alignItems: "center",
                      margin: "1.5rem 0",
                    }}
                >
                  <input
                      type="number"
                      min={1}
                      value={blockValue}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) => setBlockValue(Number(e.target.value))}
                      placeholder="請輸入數字"
                      style={{
                        flex: 1,
                        padding: "0.5rem",
                        border: "1px solid #ddd",
                        borderRadius: "4px",
                      }}
                  />
                  <select
                      value={blockUnit}
                      onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setBlockUnit(e.target.value as "day" | "week" | "month")}
                      style={{
                        padding: "0.5rem",
                        border: "1px solid #ddd",
                        borderRadius: "4px",
                        minWidth: "80px",
                      }}
                  >
                    <option value="day">天</option>
                    <option value="week">週</option>
                    <option value="month">月</option>
                  </select>
                </div>
                <div
                    style={{
                      display: "flex",
                      gap: "1rem",
                      justifyContent: "flex-end",
                      marginTop: "1.5rem",
                    }}
                >
                  <button
                      onClick={() => setShowBlockModal(false)}
                      style={{
                        padding: "0.5rem 1rem",
                        background: "#e9ecef",
                        border: "none",
                        borderRadius: "4px",
                        cursor: "pointer",
                      }}
                  >
                    取消
                  </button>
                  <button
                      onClick={handleConfirmBlock}
                      style={{
                        padding: "0.5rem 1rem",
                        background: "var(--accent)",
                        color: "white",
                        border: "none",
                        borderRadius: "4px",
                        cursor: "pointer",
                      }}
                  >
                    確認禁言
                  </button>
                </div>
              </div>
            </div>
        )}
      </div>
  );
};

export default ReviewCard;