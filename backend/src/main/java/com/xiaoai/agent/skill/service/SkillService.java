package com.xiaoai.agent.skill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.skill.entity.Skill;
import com.xiaoai.agent.skill.model.CreateSkillCommand;
import com.xiaoai.agent.skill.model.ImproveSkillCommand;
import com.xiaoai.agent.skill.model.SearchSkillQuery;

import java.util.List;

/**
 * 閹垛偓閼宠姤婀囬崝鈩冨复閸? * <p>
 * 缁狅紕鎮夾gent閸欘垰顦查悽銊ф畱閹垛偓閼虫枻绱橲kill閿涘绱濋弨顖涘瘮閹垛偓閼崇晫娈戦崚娑樼紦閵嗕焦鎮崇槐顫偓浣峰▏閻劏鎷烽煪顏傗偓浣藉殰閹存垼绻橀崠鏍ф嫲鎼寸喎绱旈妴? * 閹垛偓閼宠姤妲窤gent娴犲孩鍨氶崝鐔稿⒔鐞涘瞼娈戞禒璇插娑擃厽褰侀悙鐓庡毉閻ㄥ嫬褰叉径宥囨暏濡€崇础閿? * 閸栧懎鎯堢憴锕€褰傞弶鈥叉閵嗕焦澧界悰灞藉敶鐎圭懓鎷伴幋鎰閻滃洨鐡戦崗鍐╂殶閹诡噯绱濈€圭偟骞嘇gent閻ㄥ嫯鍤滈幋鎴濐劅娑旂姾鍏橀崝娑栤偓? * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface SkillService extends IService<Skill> {

    default List<Skill> searchSkillsByKeyword(String keyword, int limit) {
        return lambdaQuery()
                .and(wrapper -> wrapper.like(Skill::getSkillName, keyword)
                        .or()
                        .like(Skill::getDescription, keyword)
                        .or()
                        .like(Skill::getTagsJson, keyword))
                .last("limit " + limit)
                .list();
    }

    default Skill createSkillFromEntity(Skill skill) {
        save(skill);
        return skill;
    }
    /**
     * 閸掓稑缂撻幎鈧懗?     * <p>
     * 閸掓繂顫愰崠鏉ageCount=0閵嗕够uccessCount=0閵嗕够uccessRate=0閵嗕箍ersion=1閵嗕够tatus=active閵?     * 鐠佹澘缍嶉幎鈧懗鐣屾畱鐟欙箑褰傞弶鈥叉JSON閿涘澅riggerConditionJson閿涘鎷伴幍褑顢戦崘鍛啇JSON閿涘潏ontentJson閿涘鈧?     * </p>
     *
     * @param command 閸掓稑缂撻崨鎴掓姢閿涘苯瀵橀崥鐜竗illCode閵嗕够killName閵嗕够killType閵嗕浇袝閸欐垶娼禒韬测偓浣稿敶鐎瑰湱鐡?     * @return 閸掓稑缂撻崥搴ｆ畱Skill鐎圭偘缍嬮敍鍫濇儓閻㈢喐鍨氶惃鍑閸滃本妞傞梻瀛樺煈閿?     */
    Skill createSkill(CreateSkillCommand command);

    /**
     * 閺嶈宓乻killCode閼惧嘲褰囨径鍕艾active閻樿埖鈧胶娈戦幎鈧懗鏂ょ礄缁夌喐鍩涢梾鏃傤瀲閿涘绱濇稉宥呯摠閸︺劍妞傛潻鏂挎礀null閵?     *
     * @param tenantId  缁夌喐鍩汭D
     * @param skillCode 閹垛偓閼宠棄鏁稉鈧紓鏍垳
     * @return Skill鐎圭偘缍嬮敍灞肩瑝鐎涙ê婀幋鏍姜active閺冩儼绻戦崶鐎梪ll
     */
    Skill getByCode(Long tenantId, String skillCode);

    /**
     * 閹兼粎鍌ㄩ幎鈧懗?     * <p>
     * 閺€顖涘瘮娴犮儰绗呮潻鍥ㄦ姢閸滃本甯撴惔蹇ョ窗
     * <ul>
     *   <li>keyword - 閸︹暞killName閵嗕龚escription閵嗕辜agsJson娑擃厽膩缁﹤灏柊?/li>
     *   <li>skillType - 缁墽鈥橀崠褰掑帳閹垛偓閼崇晫琚崹?/li>
     *   <li>minSuccessRate - 鏉╁洦鎶ら張鈧担搴㈠灇閸旂喓宸?/li>
     *   <li>status - 姒涙顓籥ctive</li>
     * </ul>
     * 閹稿〗sageCount閸滃uccessRate閸欏瞼娣惔锕€鈧帒绨幒鎺戝灙閵?     * </p>
     *
     * @param query 閹兼粎鍌ㄩ弶鈥叉
     * @return 閹垛偓閼宠棄鍨悰?     */
    List<Skill> searchSkills(SearchSkillQuery query);

    /**
     * 鐠佹澘缍嶉幎鈧懗鐣屾畱娑撯偓濞嗏€插▏閻劎绮ㄩ弸婊愮礉閺囧瓨鏌妘sageCount閵嗕够uccessCount閸滃uccessRate閵?     * successRate鐠侊紕鐣婚弬鐟扮础閿?successCount * 100) / usageCount閿涘牊鏆ｉ弫鎵閸掑棙鐦敍澶堚偓?     *
     * @param skillId 閹垛偓閼崇祤D
     * @param success 閺堫剚顐兼担璺ㄦ暏閺勵垰鎯侀幋鎰
     */
    void recordUsage(Long skillId, boolean success);

    /**
     * 閸╄桨绨担璺ㄦ暏閸欏秹顩弨纭呯箻閹垛偓閼?     * <p>
     * 閺囧瓨鏌婇幎鈧懗鐣屾畱contentJson閿涘牊澧界悰灞藉敶鐎圭櫢绱氶敍灞借嫙鐏忓棛澧楅張顒€褰块柅鎺戭杻閵?     * 閻劋绨珹gent閻ㄥ嫯鍤滈幋鎴ｇ箻閸栨牭绱拌ぐ鎾寸厙濞嗏剝濡ч懗鑺ュ⒔鐞涘本鏅ラ弸婊€绗夋担铏閿涘本鐗撮幑顔煎冀妫ｅ牅鎱ㄩ弨瑙勫⒔鐞涘矂鈧槒绶妴?     * </p>
     *
     * @param command 閺€纭呯箻閸涙垝鎶ら敍灞藉瘶閸氱幐killId閵嗕巩mprovedContentJson閸滃mprovementReason
     * @return 閺囧瓨鏌婇崥搴ｆ畱Skill鐎圭偘缍嬮敍鍧磂rsion瀹告煡鈧帒顤冮敍?     * @throws IllegalArgumentException 婵″倹鐏夐幎鈧懗鎴掔瑝鐎涙ê婀?     */
    Skill improveSkill(ImproveSkillCommand command);

    /**
     * 娴犲孩鍨氶崝鐔风暚閹存劗娈戞禒璇插娑擃叀鍤滈崝銊﹀絹閸欐牕褰叉径宥囨暏閻ㄥ嫭濡ч懗濮愨偓?     * 娴犲懎顦╅悶鍞杢atus=completed閻ㄥ嫪鎹㈤崝鈽呯礉闁俺绻冮崚鍡樼€介幍褑顢戞潻鍥┾柤鐠囧棗鍩嗛崣顖氼槻閻劍膩瀵繈鈧?     * 瑜版挸澧犻悧鍫熸拱娑撳搫宕版担宥呯杽閻滃府绱欐潻鏂挎礀null閿涘绱濋崥搴ｇ敾鐏忓棙甯撮崗顧扡M閸掑棙鐎介柅鏄忕帆閵?     *
     * @param tenantId 缁夌喐鍩汭D
     * @param taskId   瀹告彃鐣幋鎰畱娴犺濮烮D
     * @param userId   鐟欙箑褰傞幓鎰絿閻ㄥ嫮鏁ら幋绋〥
     * @return 閹绘劕褰囬崙铏规畱Skill鐎圭偘缍嬮敍灞界秼閸撳秷绻戦崶鐎梪ll
     */
    Skill extractSkillFromTask(Long tenantId, Long taskId, Long userId);

    /**
     * 閼惧嘲褰囩粔鐔稿煕娑撳濞囬悽銊︽付妫版垹绠掓稉鏃€鍨氶崝鐔哄芳閺堚偓妤傛娈慳ctive閹垛偓閼宠棄鍨悰銊ｂ偓?     * 閹稿〗sageCount閸滃uccessRate閸欏瞼娣惔锕€鈧帒绨幒鎺戝灙閿涘苯褰囬崜宄玦mit閺壜扳偓?     *
     * @param tenantId 缁夌喐鍩汭D
     * @param limit    閺堚偓婢堆嗙箲閸ョ偞鏆熼柌?     * @return 閻戭參妫幎鈧懗钘夊灙鐞?     */
    List<Skill> getTopSkills(Long tenantId, int limit);

    /**
     * 鎼寸喎绱旈幎鈧懗鏂ょ礉鐏忓敄tatus娴犲穬ctive閺€閫涜礋deprecated閵?     * 鎼寸喎绱旈崥搴ｆ畱閹垛偓閼虫垝绗夐崘宥呭棘娑撳孩濡ч懗钘夊爱闁板秴鎷版担璺ㄦ暏閿涘奔绲鹃崢鍡楀蕉鐠佹澘缍嶆穱婵堟殌閵?     *
     * @param skillId 閹垛偓閼崇祤D
     * @throws IllegalArgumentException 婵″倹鐏夐幎鈧懗鎴掔瑝鐎涙ê婀?     */
    void deprecateSkill(Long skillId);
}
