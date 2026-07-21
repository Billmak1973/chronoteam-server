package org.example.website.controller;

import org.example.website.entity.AdminPenalty;
import org.example.website.entity.Appeal;
import org.example.website.repository.AdminPenaltyRepository;
import org.example.website.repository.AppealRepository;
import org.example.website.service.AdminPenaltyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class PenaltyAppealController {

    private final AdminPenaltyRepository adminPenaltyRepository;
    private final AppealRepository appealRepository;
    private final AdminPenaltyService adminPenaltyService;

    public PenaltyAppealController(AdminPenaltyRepository adminPenaltyRepository,
                                   AppealRepository appealRepository,
                                   AdminPenaltyService adminPenaltyService) {
        this.adminPenaltyRepository = adminPenaltyRepository;
        this.appealRepository = appealRepository;
        this.adminPenaltyService = adminPenaltyService;
    }

    @GetMapping("/penalties")
    public String managePenaltiesAndAppeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));

        adminPenaltyService.updateExpiredStatus();

        adminPenaltyService.updateExpiredAppeals();

        // 1. 獲取處罰記錄 (按開始時間倒序)
        Page<AdminPenalty> penaltiesPage = adminPenaltyRepository.findAll(pageable);

        // 2. 獲取申訴記錄 (按創建時間倒序)
        Pageable appealPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Appeal> appealsPage = appealRepository.findAll(appealPageable);

        Map<Long, String> reviewContentMap = new HashMap<>();
        for (Appeal appeal : appealsPage.getContent()) {
            // 根据申诉ID去查找对应的处罚记录
            Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByAppealId(appeal.getAppealId());

            if (penaltyOpt.isPresent()) {
                // 找到了，把评论内容放入 Map
                reviewContentMap.put(appeal.getAppealId(), penaltyOpt.get().getReviewContent());
            } else {
                // 没找到（可能是旧数据），放个空字符串或者提示
                reviewContentMap.put(appeal.getAppealId(), "无关联处罚记录");
            }
        }

        model.addAttribute("penalties", penaltiesPage.getContent());
        model.addAttribute("penaltyTotalPages", penaltiesPage.getTotalPages());
        model.addAttribute("penaltyCurrentPage", page);

        model.addAttribute("appeals", appealsPage.getContent());
        model.addAttribute("appealTotalPages", appealsPage.getTotalPages());
        model.addAttribute("appealCurrentPage", page);
        model.addAttribute("reviewContentMap", reviewContentMap);

        return "admin/admin-penalties";
    }

}