package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.Keyword;
import org.example.website.repository.KeywordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordService {
    private final KeywordRepository keywordRepository;

    public List<Keyword> getAllKeywords() {
        return keywordRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Keyword addKeyword(String keywordContent) {
        String trimmed = keywordContent.trim();
        if (trimmed.isEmpty()) {
            throw new RuntimeException("關鍵詞不能為空");
        }
        if (keywordRepository.existsByKeyword(trimmed)) {
            throw new RuntimeException("該關鍵詞已存在");
        }
        Keyword keyword = new Keyword();
        keyword.setKeyword(trimmed);
        return keywordRepository.save(keyword);
    }

    @Transactional
    public Keyword updateKeyword(Long keywordId, String newKeywordContent) {
        String trimmed = newKeywordContent.trim();
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new RuntimeException("關鍵詞不存在"));

        if (!keyword.getKeyword().equals(trimmed) && keywordRepository.existsByKeyword(trimmed)) {
            throw new RuntimeException("新關鍵詞已存在");
        }
        keyword.setKeyword(trimmed);
        return keywordRepository.save(keyword);
    }

    @Transactional
    public void deleteKeyword(Long keywordId) {
        keywordRepository.deleteById(keywordId);
    }
}