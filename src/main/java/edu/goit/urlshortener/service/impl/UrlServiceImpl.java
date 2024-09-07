package edu.goit.urlshortener.service.impl;

import edu.goit.urlshortener.model.Url;
import edu.goit.urlshortener.model.responses.ShortLinkResponse;
import edu.goit.urlshortener.repo.UrlRepository;
import edu.goit.urlshortener.repo.UserRepository;
import edu.goit.urlshortener.security.model.User;
import edu.goit.urlshortener.service.UrlService;
import edu.goit.urlshortener.util.Base62Encoder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UrlServiceImpl implements UrlService {
    private final UserRepository userRepository;
    private final UrlRepository urlRepository;
    private final RedisTemplate<String, Url> redisTemplate;

    @Transactional
    public String createShortLink(String longUrl) {
        User authUser = getUser();

        Url url = Url.builder()
                .nativeLink(longUrl)
                .shortLink("") // Temporary value
                .clickCount(0L)
                .createdAt(LocalDateTime.now())
                .expiredTime(LocalDateTime.now().plusMonths(1))
                .user(authUser)
                .build();

        Url saved = urlRepository.save(url);
        String slugStr = Base62Encoder.encode(saved.getId());
        saved.setShortLink(slugStr);
        return "https://localhost:8080/" + slugStr;
    }

    public String getDestinationLink(String shortLink) {
        String cacheKey = "urlCache::" + shortLink;
        Url url = redisTemplate.opsForValue().get(cacheKey);

        if (url == null) {
            url = urlRepository.findByShortLink(shortLink)
                    .orElseThrow(EntityNotFoundException::new);
            redisTemplate.opsForValue().set(cacheKey, url);
        }

        if (url.getExpiredTime().isAfter(LocalDateTime.now())) {
            Long newClickCount = redisTemplate.opsForValue().increment(cacheKey + "::clickCount", 1);
            url.setClickCount(newClickCount);
            redisTemplate.opsForValue().set(cacheKey, url);
            return url.getNativeLink();
        }

        return "Your link has expired:" + url.getShortLink();
    }

    @Cacheable(value = "shortLinkResponses", key = "#shortLink", unless = "#result == null")
    public ShortLinkResponse getShortLinkDto(String shortLink) {
        Url url = urlRepository.findByShortLink(shortLink).orElseThrow(EntityNotFoundException::new);
        return ShortLinkResponse.builder()
                .clickCount(url.getClickCount())
                .slug(url.getShortLink())
                .destination(url.getNativeLink())
                .createdAt(url.getCreatedAt())
                .expiredTime(url.getExpiredTime())
                .build();
    }

    public Page<String> findAllActiveUrls(Pageable pageable) {
        User authUser = getUser();

        List<String> slugsList = urlRepository.findAllActiveSlugsByUserId(authUser)
                .orElseThrow(EntityNotFoundException::new);
        return new PageImpl<>(slugsList, pageable, slugsList.size());
    }



    @CacheEvict(value = "urlCache", key = "#shortLink")
    @Transactional
    public void deleteShortLink(String shortLink) {
        Url url = urlRepository.findByShortLink(shortLink)
                .orElseThrow(() -> new EntityNotFoundException("Short link not found"));
        urlRepository.delete(url);
    }

    public Page<String> findAllLinks(int offset, int size) {
        User authUser = getUser();

        Pageable pageable = PageRequest.of(offset, size);
        List<String> list = urlRepository.findAllShortLinkByUserId(authUser)
                .orElseThrow(EntityNotFoundException::new);
        return new PageImpl<>(list, pageable, list.size());
    }

    @Transactional
    public void extendExpirationDate(String shortLink) {
        User authUser = getUser();

        Url url = urlRepository.findUrlByShortLinkAndUser(shortLink, authUser)
                .orElseThrow(EntityNotFoundException::new);
        url.setExpiredTime(LocalDateTime.now().plusDays(30));

        String cacheKey = "urlCache::" + shortLink;
        redisTemplate.opsForValue().set(cacheKey, url);
    }

    private User getUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username " + username));
    }
}
