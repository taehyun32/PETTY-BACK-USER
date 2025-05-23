package io.github.petty.llm.service;

import io.github.petty.llm.common.AreaCode;
import io.github.petty.llm.common.ContentType;
import io.github.petty.llm.dto.EmbeddingResult;
import io.github.petty.tour.entity.Content;
import io.github.petty.tour.entity.PetTourInfo;
import io.github.petty.tour.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final ContentRepository contentRepository;
    // OpenAI 임베딩 모델
    private final EmbeddingModel embeddingModel;
    private final GeminiPreprocessorService geminiPreprocessorService;

    /**
     * 콘텐츠 객체를 기반으로 자연스러운 문장 텍스트를 생성합니다.
     * 생성된 텍스트는 장소 설명, 반려 동물 정보 등을 포함하여 유사도 검색에 적합한 형식으로 반환됩니다.
     *
     * @param content 콘텐츠 정보를 담고 있는 {@link Content} 객체. 이 객체는 장소의 제목, 주소, 개요 및
     *                반려 동물 정보 등을 포함하며, 텍스트 생성을 위해 사용됩니다.
     * @return 장소 설명과 반려 동물 정보를 포함한 자연스러운 문장의 텍스트 문자열을 반환합니다.
     */
    // 1. 텍스트 전처리 : 장소 설명들 다 모아서 문장으로 만들기
    public String prepareContentText(Content content) {
        PetTourInfo pet = content.getPetTourInfo();
        if (pet != null && "불가능".equals(pet.getAcmpyTypeCd())) {
            // 반려동물 동반 불가능이면 아예 텍스트 생성 안 함
            return null;
        }
        
        // 동반 가능할 때 생성
//        StringBuilder sb = new StringBuilder();

        // 유사도 검색용 자연어 생성
//        String title = content.getTitle();
//        String addr1 = content.getAddr1() != null ? content.getAddr1() : "";
//        String addr2 = content.getAddr2() != null ? content.getAddr2() : "";
//        String ContentTypeName = ContentType.fromCode(content.getContentTypeId()).getName();
//
//        sb.append("%s은/는 %s %s에 위치한 %s 장소입니다.\n"
//                .formatted(title, addr1, addr2, ContentTypeName));
//
//        if (content.getOverview() != null) {
//            sb.append("이곳의 설명은 ").append(content.getOverview()).append("\n");
//        }
        String preprocessedText = geminiPreprocessorService.preprocessContent(content);
        return preprocessedText;
    }


    /**
     * 주어진 EmbeddingResult와 Content를 Document 객체로 변환합니다.
     이 메서드는 고유한 식별자를 할당하면서 content ID, 제목, 지역 코드, 콘텐츠 유형과 같은 메타데이터를 포함하여 문서를 생성합니다.

     @param result 임베딩 데이터와 관련된 content ID를 포함하는 EmbeddingResult 객체
     @param content 제목 및 지역 코드, 콘텐츠 유형 ID와 같은 추가 메타데이터를 포함한 Content 객체
     @return 입력 매개변수에서 생성된 ID, 텍스트, 메타데이터를 포함하는 Document 객체
     */

    // 임베딩 생성 - UUID와 원본 ID 모두 포함해야 함
    // dto 사용
    // VectorService에서 문서 생성만 하도록 위임
    public Document toDocument(EmbeddingResult result, Content content){

        // 메타데이터에 원본 ID, 관련 데이터 저장
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("contentId", result.contentId());
        metadata.put("title", content.getTitle());

        // 1. areaCode 처리
        Integer areaCode = content.getAreaCode();
        if (areaCode == null || areaCode == 0) {
            String addr1 = content.getAddr1();
            if (addr1 != null && !addr1.isBlank()) {
                String[] parts = addr1.split(" ");
                if (parts.length > 0) {
                    areaCode = AreaCode.fromName(parts[0]).getCode();
                }
            }
        }
        metadata.put("areaCode", areaCode);

        // 2. sigunguCode 처리
        Integer sigunguCode = content.getSigunguCode();
        if (sigunguCode == null || sigunguCode == 0) {
            String addr1 = content.getAddr1();
            if (addr1 != null && !addr1.isBlank()) {
                String[] parts = addr1.split(" ");
                if (parts.length > 1) {
                    // 간단히 두 번째 단어를 문자열 형태로 저장
                    metadata.put("sigungu", parts[1]);
                }
            }
        } else {
            // 기존 sigunguCode가 있으면 그대로
            metadata.put("sigunguCode", sigunguCode);
        }
        // 지역 String 추가
        metadata.put("address", content.getAddr1());
        // contentType (콘텐츠 관련 추가)
        metadata.put("contentType", content.getContentTypeId());

        // 문서 생성 - UUID -> 문서 ID
        return Document.builder()
                .id(result.id())
                .text(result.text())
                .metadata(metadata)
                .build();

    }

    /**
     * 주어진 콘텐츠를 텍스트로 정제한 후 임베딩 벡터를 생성하고 결과를 반환합니다.
     *
     * @param content 텍스트로 정제하고 임베딩할 콘텐츠 객체
     * @return 콘텐츠의 UUID, 원본 contentId, 정제된 텍스트, 임베딩 벡터를 포함한 결과 객체
     */
    // 텍스트 정제 -> 임베딩
    public EmbeddingResult embedContent(Content content) {
        String text = prepareContentText(content);
        // text 비었을 때
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding할 text가 비어있습니다: contentId=" + content.getContentId());
        }
        
        // 임베딩 벡터 생성
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        float[] output = response.getResults().get(0).getOutput();
         // 수동 변환
        List<Float> vector = new ArrayList<>();
        for (float f : output) {
            vector.add(f);
        }
        // UUID 생성
        String uuid = UUID.randomUUID().toString();

        // 원본 contentId 보존
        String contentId = content.getContentId().toString();

        // content id + 원문 텍스트 + 임베딩 벡터 반환
        return new EmbeddingResult(uuid, contentId, text, vector);
    }


}
