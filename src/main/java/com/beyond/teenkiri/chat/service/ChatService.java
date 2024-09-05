package com.beyond.teenkiri.chat.service;

import com.beyond.teenkiri.chat.domain.Chat;
import com.beyond.teenkiri.chat.dto.ChatMessageDto;
import com.beyond.teenkiri.chat.repository.ChatRepository;
import com.beyond.teenkiri.user.domain.User;
import com.beyond.teenkiri.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatService implements MessageListener {

    @Qualifier("chatRedisTemplate")  // 채팅 전용 RedisTemplate 사용
    private final RedisTemplate<String, Object> chatRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;  // WebSocket 메시지 전송

    // 금지어 패턴을 저장할 Set
    private Set<Pattern> forbiddenWordsPatterns = new HashSet<>();

    // 애플리케이션 시작 시 금지어 파일을 로드
    @PostConstruct
    public void init() {
        loadForbiddenWordsFromFile();
    }

    // 금지어 파일을 로드하고 패턴을 Set에 저장
    private void loadForbiddenWordsFromFile() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("badwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String word;
            while ((word = reader.readLine()) != null) {
                forbiddenWordsPatterns.add(Pattern.compile("\\b" + word + "\\b", Pattern.CASE_INSENSITIVE));
            }
        } catch (Exception e) {
            log.error("Error reading forbidden words file", e);
        }
    }

    // 금지어 필터링 로직
    public String filterMessage(String content) {
        for (Pattern pattern : forbiddenWordsPatterns) {
            content = pattern.matcher(content).replaceAll(m -> "*".repeat(m.group().length()));
        }
        return content;
    }

    // 메시지 저장 및 금지어 필터링 적용 후 메시지 저장
    public ChatMessageDto saveMessage(ChatMessageDto chatMessageDto) {
        log.debug("Received email: {}", chatMessageDto.getEmail());

        // 이메일이 비어있으면 예외 처리
        if (chatMessageDto.getEmail() == null || chatMessageDto.getEmail().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }

        // 이메일로 사용자 정보 조회
        User user = userRepository.findByEmailIgnoreCase(chatMessageDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        log.debug("User found: {}", user.getEmail());

        // 메시지 필터링 적용
        String filteredContent = filterMessage(chatMessageDto.getContent());
        chatMessageDto.setContent(filteredContent);
        chatMessageDto.setSenderNickname(user.getNickname());  // 닉네임 설정

        Chat chat = chatMessageDto.toEntity(user);
        Chat savedChat = chatRepository.save(chat);

        ChatMessageDto responseMessage = ChatMessageDto.fromEntity(savedChat);

        messagingTemplate.convertAndSend("/topic/" + chatMessageDto.getChannel(), responseMessage);

        chatRedisTemplate.convertAndSend("/topic/" + chatMessageDto.getChannel(), responseMessage);

        return responseMessage;
    }

    // Redis Pub/Sub으로 받은 메시지를 WebSocket으로 전송하는 로직
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String msg = new String(message.getBody());
            ChatMessageDto chatMessageDto = objectMapper.readValue(msg, ChatMessageDto.class);
            log.info("Received message from Redis: {}", chatMessageDto);

            // 받은 메시지를 WebSocket을 통해 해당 채널로 브로드캐스트
            messagingTemplate.convertAndSend("/topic/" + chatMessageDto.getChannel(), chatMessageDto);

        } catch (Exception e) {
            log.error("Failed to process received message", e);
        }
    }

    // 특정 시간 이후의 채팅 메시지를 조회
    public List<ChatMessageDto> getMessagesSince(LocalDateTime since) {
        return chatRepository.findByCreatedTimeAfter(since)
                .stream()
                .map(ChatMessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 모든 채팅 메시지를 조회하는 메서드 (옵션)
    public List<ChatMessageDto> getAllMessages() {
        return chatRepository.findAll().stream()
                .map(ChatMessageDto::fromEntity)
                .collect(Collectors.toList());
    }
}
