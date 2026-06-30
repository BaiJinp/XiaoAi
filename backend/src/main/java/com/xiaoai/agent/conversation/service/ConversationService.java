package com.xiaoai.agent.conversation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.conversation.entity.Conversation;
import com.xiaoai.agent.conversation.entity.ConversationMessage;

import java.util.List;

/**
 * 鐎电鐦介張宥呭閹恒儱褰? * <p>
 * 缁狅紕鎮婇悽銊﹀煕娑撳盯gent娑斿妫块惃鍕嚠鐠囨繂宸婚崣璇х礉閹绘劒绶电€电鐦介崚娑樼紦閵嗕焦绉烽幁顖濇嫹閸旂姰鈧礁甯囩紓鈹库偓浣规偝缁鳖潿鈧礁鍨庨弨顖樷偓浣告礀闁偓閵嗕礁顕遍崙鍝勬嫲瑜版帗銆傜粵澶婂閼冲鈧? * 鐎电鐦介弰鐤塯ent婢舵俺鐤嗘禍銈勭鞍閻ㄥ嫭鐗宠箛鍐祰娴ｆ搫绱濆В蹇旀蒋濞戝牊浼呯拋鏉跨秿role閿涘澆ser/assistant/system閿涘鎷癱ontent閵? * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface ConversationService extends IService<Conversation> {

    /**
     * 閸掓稑缂撻弬鏉款嚠鐠?     * <p>
     * 閸掓繂顫愰崠鏍︾娑擃亞鈹栭惃鍕嚠鐠囨繀绱扮拠婵撶礉閸忓疇浠堥幐鍥х暰閻ㄥ嫮顫ら幋鏋偓浣烘暏閹村嘲鎷癆gent閵?     * 鐎电鐦介崚婵嗩潗閻樿埖鈧椒璐焌ctive閿涘苯褰查柅姘崇箖title鐠佸墽鐤嗙€电鐦芥稉濠氼暯閵?     * </p>
     *
     * @param tenantId 缁夌喐鍩汭D
     * @param userId   閻劍鍩汭D
     * @param agentId  Agent ID
     * @param title    鐎电鐦介弽鍥暯閿涘牆褰查柅澶涚礆
     * @return 閸掓稑缂撻崥搴ｆ畱Conversation鐎圭偘缍?     */
    Conversation createConversation(Long tenantId, Long userId, Long agentId, String title);

    /**
     * 閸氭垵顕拠婵婃嫹閸旂姳绔撮弶鈩冪Х閹?     * <p>
     * 閸︺劌顕拠婵囨汞鐏忔崘鎷烽崝鐘辩閺夆剝瀵氱€规ole閻ㄥ嫭绉烽幁顖ょ礉role闁艾鐖舵稉?user"閵?assistant"閹?system"閵?     * 閸欘垶鈧鍙ч懕鏀朼skId閿涘瞼鏁ゆ禍搴ゆ嫹闊亞鏁遍崫顏冮嚋Task娴溠呮晸閻ㄥ嫭绉烽幁顖樷偓?     * </p>
     *
     * @param conversationId 鐎电鐦絀D
     * @param role           濞戝牊浼呯憴鎺曞閿涘澆ser/assistant/system閿?     * @param content        濞戝牊浼呴崘鍛啇
     * @param taskId         閸忓疇浠堥惃鍕崲閸旑搹D閿涘牆褰查柅澶涚礉閸欘垯璐焠ull閿?     * @return 閸掓稑缂撻崥搴ｆ畱ConversationMessage鐎圭偘缍?     */
    ConversationMessage addMessage(Long conversationId, String role, String content, Long taskId);

    default ConversationMessage addMessage(ConversationMessage message) {
        return addMessage(message.getConversationId(), message.getRole(), message.getContent(), message.getTaskId());
    }

    default ConversationMessage updateMessage(ConversationMessage message) {
        return message;
    }

    /**
     * 閼惧嘲褰囩€电鐦介惃鍕閺堝绉烽幁顖氬灙鐞涱煉绱濋幐澶婂絺闁焦妞傞梻鎾€庢惔蹇斿笓閸掓ぜ鈧?     *
     * @param conversationId 鐎电鐦絀D
     * @return 濞戝牊浼呴崚妤勩€?     */
    List<ConversationMessage> getMessages(Long conversationId);

    /**
     * 閸樺缂夌€电鐦介崢鍡楀蕉
     * <p>
     * 鐏忓棗顕拠婵堟畱鐎瑰本鏆ｅ☉鍫熶紖閸樺棗褰堕崢瀣級娑撹桨绔村▓鍨喅鐟曚焦鏋冮張顒婄礉閻劋绨崙蹇撶毌LLM娑撳﹣绗呴弬鍥毐鎼达负鈧?     * 閸樺缂夐崥搴″斧婵绉烽幁顖欑箽閻ｆ瑱绱濋幗妯款洣鐎涙ê鍋嶉崷鈥搊nversation閻ㄥ墕ummaryText鐎涙顔屾稉顓溾偓?     * </p>
     *
     * @param conversationId 鐎电鐦絀D
     * @return 閸樺缂夐崥搴ｆ畱閹芥顩﹂弬鍥ㄦ拱
     */
    String compressConversation(Long conversationId);

    /**
     * 閹兼粎鍌ㄧ€电鐦?     * <p>
     * 閸︺劎顫ら幋椋庢畱鐎电鐦介弽鍥暯娑擃叀绻樼悰灞藉彠闁款喛鐦濆Ο锛勭ˇ閹兼粎鍌ㄩ敍灞惧瘻閺堚偓鏉╂垶妞跨捄鍐╂闂傛潙鈧帒绨幒鎺戝灙閵?     * </p>
     *
     * @param tenantId 缁夌喐鍩汭D
     * @param userId   閻劍鍩汭D
     * @param keyword  閹兼粎鍌ㄩ崗鎶芥暛鐠囧稄绱欓崷銊︾垼妫版ü鑵戦崠褰掑帳閿?     * @param limit    閺堚偓婢堆嗙箲閸ョ偞鏆熼柌?     * @return 閸栧綊鍘ら惃鍕嚠鐠囨繂鍨悰?     */
    List<Conversation> searchConversations(Long tenantId, Long userId, String keyword, int limit);

    /**
     * 娴犲骸顕拠婵堟畱閺屾劒閲滃☉鍫熶紖閼哄倻鍋ｉ崚娑樼紦閸掑棙鏁?     * <p>
     * 婢跺秴鍩楃€电鐦芥稉鐠猺anchPointMessageId閸欏﹣绠ｉ崜宥囨畱閹碘偓閺堝绉烽幁顖氬煂閺傛澘顕拠婵撶礉
     * 閻劋绨崷銊ヮ嚠鐠囨繂宸婚崣韫厬"閸掑棗寮?閹恒垻鍌ㄦ稉宥呮倱閻ㄥ嫬顕拠婵婄熅瀵板嫨鈧?     * </p>
     *
     * @param conversationId       閸樼喎顕拠婊籇
     * @param branchPointMessageId 閸掑棙鏁挧椋庡仯濞戝牊浼匢D閿涘牆瀵橀崥顐ヮ嚉濞戝牊浼呴敍?     * @param newTitle             閺傛澘顕拠婵囩垼妫?     * @return 閺傛澘鍨卞铏规畱閸掑棙鏁瓹onversation鐎圭偘缍?     */
    Conversation branchConversation(Long conversationId, Long branchPointMessageId, String newTitle);

    /**
     * 閸ョ偤鈧偓鐎电鐦介崚鐗堝瘹鐎规碍绉烽幁?     * <p>
     * 閸掔娀娅巑essageId娑斿鎮楅惃鍕閺堝绉烽幁顖ょ礉閻劋绨幘銈夋敘闁挎瑨顕ら惃鍕嚠鐠囨繃顒炴銈嗗灗闁插秵鏌婇幒銏㈠偍閵?     * messageId閺堫剝闊╂穱婵堟殌閿涘矁顕氬☉鍫熶紖娑斿鎮楁潻钘夊閻ㄥ嫭澧嶉張澶嬬Х閹垰鐨㈢悮顐㈠灩闂勩們鈧?     * </p>
     *
     * @param conversationId 鐎电鐦絀D
     * @param messageId      閸ョ偤鈧偓閻╊喗鐖ｅ☉鍫熶紖ID閿涘牅绻氶悾娆掝嚉濞戝牊浼呴敍?     */
    void rollbackToMessage(Long conversationId, Long messageId);

    /**
     * 鐎电厧鍤€电鐦芥稉鐑樺瘹鐎规碍鐗稿蹇曟畱鐎涙顑佹稉?     * <p>
     * 閺€顖涘瘮閺嶇厧绱￠敍?     * <ul>
     *   <li>"json" - JSON閺嶇厧绱￠敍灞芥儓鐎电鐦介崗鍐╂殶閹诡喖鎷伴幍鈧張澶嬬Х閹?/li>
     *   <li>"markdown" - Markdown閺嶇厧绱￠敍灞肩┒娴滃酣妲勭拠?/li>
     *   <li>閸忔湹绮?- 缁绢垱鏋冮張顒佺壐瀵?/li>
     * </ul>
     * </p>
     *
     * @param conversationId 鐎电鐦絀D
     * @param format         鐎电厧鍤弽鐓庣础閿?json"閵?markdown"閹存牕鍙炬禒?     * @return 閺嶇厧绱￠崠鏍ф倵閻ㄥ嫬顕拠婵嗙摟缁楋缚瑕?     */
    String exportConversation(Long conversationId, String format);

    /**
     * 瑜版帗銆傜€电鐦介敍灞界殺鐎电鐦介悩鑸碘偓浣风矤active閺€閫涜礋archived閵?     * 瑜版帗銆傞崥搴☆嚠鐠囨繀绗夐崘宥呭毉閻滄澘婀ú鏄忕┈閸掓銆冩稉顓ㄧ礉娴ｅ棗宸婚崣鍙夌Х閹垯绻氶悾娆忓讲閺屻儯鈧?     *
     * @param conversationId 鐎电鐦絀D
     */
    void archiveConversation(Long conversationId);

    /**
     * 閼惧嘲褰囩€电鐦界紒鐔活吀娣団剝浼呴敍灞藉瘶閹奉剚绉烽幁顖涒偓缁樻殶閵嗕焦鈧焙oken閺佽埇鈧胶鏁ら幋閿嬬Х閹垱鏆熼崪灞藉И閹靛绉烽幁顖涙殶閵?     *
     * @param conversationId 鐎电鐦絀D
     * @return ConversationStats缂佺喕顓哥€电钖?     */
    ConversationStats getStats(Long conversationId);

    /**
     * 鐎电鐦界紒鐔活吀閺佺増宓佺€电钖?     * <p>
     * 鐠佹澘缍嶇€电鐦介惃鍕櫤閸栨牗瀵氶弽鍥风礉閻劋绨仦鏇犮仛鐎电鐦界憴鍕侀崪灞剧Х閼版鍎忛崘鐐光偓?     * </p>
     */
    class ConversationStats {
        /** 濞戝牊浼呴幀缁樻殶閿涘牆瀵橀崥鐜約er閵嗕工ssistant閵嗕够ystem閹碘偓閺堝顫楅懝璇х礆 */
        public int messageCount;
        /** 娴兼壆鐣婚惃鍕偓绫簅ken閺佸府绱欓悽銊ょ艾閹存劖婀伴弽鍝ョ暬閿?*/
        public long totalTokens;
        /** 閻劍鍩涢崣鎴︹偓浣烘畱濞戝牊浼呴弫?*/
        public int userMessageCount;
        /** Agent閸ョ偛顦查惃鍕Х閹垱鏆?*/
        public int assistantMessageCount;
    }
}
