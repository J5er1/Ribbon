import Foundation

// The cards (§4.6): reflection questions that open once everyone has
// answered. Open question §16.4 — who writes them — is resolved here as its
// first option: Ribbon-authored cards on the five "good places to start"
// books, and no card anywhere else (S03 allows a chapter without one).
// "A room writes its own" stays open for later.
//
// The register is the brief's (§12): short, concrete, second person,
// answerable in a sentence. "What did you notice that the other one
// probably didn't?" is the model; "How is God calling you to grow?" is the
// register to avoid. The tests pin the shape so an edit can't drift it.
//
// Deterministic by design: two devices minting the same card lazily must
// write the same question.

public enum CardQuestions {
    /// One question per chapter, or nil where the book carries no cards.
    public static func question(bookID: String, chapter: Int) -> String? {
        if bookID == "PSA" {
            if let specific = psalms[chapter] { return specific }
            guard chapter >= 1 else { return nil }
            return psalmRotation[(chapter - 1) % psalmRotation.count]
        }
        return table[bookID]?[chapter]
    }

    /// The books that carry cards at all.
    public static var books: [String] { Array(table.keys) + ["PSA"] }

    static let table: [String: [Int: String]] = [
        "MRK": [
            1: "Everything in this chapter happens \"immediately.\" Where did you want it to slow down?",
            2: "Four friends cut a hole in a roof. Who has gone to that kind of trouble for you?",
            3: "His own family came to take him home. What in this chapter would have worried you, if you were them?",
            4: "Which of the four soils did you picture most clearly, and what did it look like?",
            5: "Jairus had to wait while Jesus talked to someone else. What did you make of the waiting?",
            6: "Which scene in this chapter would you have wanted to be in, and which would you have skipped?",
            7: "The woman argued back, and he changed his answer. What did you notice about how she said it?",
            8: "The blind man saw people \"like trees, walking\" before he saw clearly. What can you only half-see right now?",
            9: "Peter wanted to build shelters and stay on the mountain. Where would you have wanted to stay?",
            10: "He asked Bartimaeus, \"What do you want me to do for you?\" How would you have answered?",
            11: "Which surprised you more: the crowd with the branches, or the tables going over?",
            12: "The widow gave two small coins, and he noticed. What small thing did you notice today?",
            13: "Which line in this chapter did you read twice?",
            14: "Peter followed at a distance. Where would you have been standing that night?",
            15: "Joseph of Arimathea asked for the body. Who in this chapter did you find yourself watching?",
            16: "The women ran and said nothing, because they were afraid. Who would you have told first?",
        ],
        "RUT": [
            1: "Orpah went home and Ruth stayed. Which one did you understand more?",
            2: "Boaz told his workers to drop grain on purpose. What's the quietest kindness anyone has done for you?",
            3: "Naomi made the plan; Ruth carried it out. Which one are you, usually?",
            4: "The book ends with a baby and a list of names. Why do you think it ends there?",
        ],
        "PHP": [
            1: "He wrote this from a prison cell. Which sentence would you least expect from someone in one?",
            2: "There's a line here about looking to the interests of others. Who came to mind?",
            3: "\"Forgetting what is behind.\" What's one thing you'd be glad to leave behind this year?",
            4: "\"Whatever is true, whatever is lovely.\" What's one thing from today that fits the list?",
        ],
        "JHN": [
            1: "\"Come and see.\" Who once said that to you, about anything?",
            2: "Only the servants knew where the wine came from. What did you notice that most of the party missed?",
            3: "Nicodemus came at night. What would you have asked, if no one was watching?",
            4: "She left her water jar at the well. What made her forget it?",
            5: "\"Do you want to get well?\" is an odd thing to ask. Why do you think he asked it?",
            6: "Many of his followers left after this chapter. Which part would have been hardest for you to stay through?",
            7: "Nicodemus spoke up once, carefully. When did you last speak up carefully?",
            8: "He wrote in the dust and didn't look up. What do you think he was giving them time to do?",
            9: "His parents wouldn't answer for him. What did you make of them?",
            10: "\"They know his voice.\" Whose voice would you know anywhere?",
            11: "Both sisters said the same thing: \"If you had been here.\" What did you hear in it?",
            12: "Judas said the perfume was a waste. Was it?",
            13: "Peter didn't want his feet washed. What's something you find hard to let someone do for you?",
            14: "Thomas said, \"We don't know the way.\" Which question in this chapter would you have asked?",
            15: "\"I have called you friends.\" What does that word change, if anything?",
            16: "\"A little while.\" What are you waiting out right now?",
            17: "This whole chapter is a prayer for other people. Who would you put in yours?",
            18: "Pilate asked, \"What is truth?\" and didn't wait for an answer. What did you make of him?",
            19: "From the cross he gave his mother a new son. What did you notice in that small scene?",
            20: "Thomas wanted to see for himself. Would you have?",
            21: "He asked Peter the same question three times. What would three times have done to you?",
        ],
    ]

    static let psalms: [Int: String] = [
        1: "Two roads and a tree by water. Which picture stayed with you?",
        8: "\"What is man that you are mindful of him?\" When did you last feel small in a good way?",
        13: "\"How long?\" Four times. What are you asking that about?",
        19: "The heavens \"pour forth speech\" without a word. What said something to you today without saying anything?",
        22: "It starts with \"why have you forsaken me\" and ends at a feast. Where did the turn happen for you?",
        23: "You've probably heard this one before. What did you notice this time?",
        27: "\"One thing I ask.\" What's your one thing?",
        42: "\"Why are you downcast, O my soul?\" He's talking to himself. What do you say to yourself on those days?",
        46: "\"Be still.\" What's the loudest thing in your week right now?",
        51: "This was written after something went badly wrong. What did you notice about how he says it?",
        63: "\"A dry and weary land.\" Where's yours?",
        84: "\"Better is one day in your courts than a thousand elsewhere.\" What place would you say that about?",
        90: "\"Teach us to number our days.\" What would you do differently if you did?",
        91: "This one gets quoted a lot. Which line would you actually want on a bad night?",
        103: "\"He remembers that we are dust.\" Is that a comfort to you, or not?",
        121: "\"I lift my eyes to the hills.\" Where do you look when you're waiting for help?",
        130: "\"More than watchmen wait for the morning.\" What's the longest you've waited for a morning?",
        139: "There's nowhere in this psalm he isn't. Is that comforting, or not?",
        150: "Every instrument in the band. Which one would you play?",
    ]

    /// Every psalm without a question of its own gets one of these, keyed
    /// by its number so it is the same on every device.
    static let psalmRotation: [String] = [
        "Which line of this psalm would you want read to you on a bad day?",
        "This was a song. What would it sound like, if you had to pick a tune?",
        "Which verse did you read twice?",
        "Is the person singing this doing well or badly? How can you tell?",
        "Pick one line from this psalm to send to someone today. Which one?",
        "What's the one image in this psalm you could draw?",
    ]

    /// Every question, for the tests that pin the register.
    public static var allQuestions: [String] {
        table.values.flatMap { $0.values } + Array(psalms.values) + psalmRotation
    }
}
