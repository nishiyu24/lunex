package com.nishiyu.lunex.datagen;

public class ModMessages implements ITranslationGatherer {
    @Override
    public void gatherTranslations(AutoLanguageProvider provider, String locale) {
        // グループ名
        provider.addTranslation("itemGroup.lunex", "Lunex", "Lunex");
        // システムメッセージ
        provider.addTranslation("message.lunex.program_stopped", "Program stopped.", "プログラムを停止しました");
        provider.addTranslation("message.lunex.program_empty", "Execution Error: No program registered in Ch %s.", "実行エラー: Ch %s にはプログラムが登録されていません");
        provider.addTranslation("message.lunex.program_started", "Program started in Ch %s.", "Ch %s のプログラムを起動しました");
        provider.addTranslation("message.lunex.error_turtle_only", "[Error] This command is exclusive to Turtle Bots.", "[エラー] このコマンドはタートルボット専用です");
    }
}