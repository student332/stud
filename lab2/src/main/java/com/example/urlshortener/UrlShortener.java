package com.example.urlshortener;

import com.google.common.hash.Hashing;
import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Controller
public class UrlShortener {

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    Duration timeToLive = Duration.ofMinutes(5);

    @GetMapping
    public String main(Map<String, Object> model) {
        model.put("shorted", "");
        return "main";
    }

    @GetMapping("/{hash}/go")
    public String goToUrl(@PathVariable String hash)
    {
        String url = redisTemplate.opsForHash().get(hash, "url").toString();
        redisTemplate.opsForHash().increment(hash, "count", 1);
        return "redirect:" + url;
    }

    @GetMapping("/{hash}")
    public String lookForUrl(@PathVariable String hash, Model model)
    {
        putCommentsToModel(hash, model);

        return "comments";
    }

    private void putCommentsToModel(String hash, Model model)
    {
        var list = redisTemplate.opsForList().range(hash+":comments",0 , -1);

        Map<String, Object> map = new HashMap<>();
        map.put("text", list);
        model.addAttribute("comments", map);
    }

    @PostMapping
    public String create(@RequestParam String url, Map<String, Object> model, HttpServletRequest request) {

        UrlValidator urlValidator = new UrlValidator(
                new String[]{"http", "https"}
        );

        if (urlValidator.isValid(url)) {
            String hash = Hashing.murmur3_32().hashString(url, StandardCharsets.UTF_8).toString();
            int count = 0;
            if (redisTemplate.opsForHash().hasKey(hash, "url") )
            {
                count = (int)redisTemplate.opsForHash().get(hash, "count");
            }
            else {
                redisTemplate.opsForHash().put(hash,"url", url);
                redisTemplate.opsForHash().put(hash,"count", 0);
                redisTemplate.expire(hash, timeToLive);
            }


            String baseURL = request.getRequestURL().toString();
            model.put("shorted", "Your beautiful URL: " + baseURL + hash + "\nLink clicks: " + count);
            return "main";
        }

        throw new RuntimeException("Invalid URL: " + url);
    }

    @PostMapping(path = "/{hash}", params = "comment")
    public String comment(@PathVariable String hash, @RequestParam String comment,Model model)
    {
        redisTemplate.opsForList().leftPush(hash+":comments", comment);
        putCommentsToModel(hash, model);
        return "comments";
    }

}
