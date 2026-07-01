import re

f1 = 'src/main/java/com/JanSahayak/AI/repository/SocialPostRepo.java'
with open(f1, 'r', encoding='utf-8') as f: text = f.read()
text = re.sub(r'    @EntityGraph\(attributePaths = \{"user", "community"\}\)\r?\n    List<SocialPost>', '    List<SocialPost>', text)
with open(f1, 'w', encoding='utf-8') as f: f.write(text)

f2 = 'src/main/java/com/JanSahayak/AI/repository/PostRepo.java'
with open(f2, 'r', encoding='utf-8') as f: text2 = f.read()
text2 = re.sub(r'    @EntityGraph\(attributePaths = \{"user", "user\.role"\}\)\r?\n    List<Post>', '    List<Post>', text2)
with open(f2, 'w', encoding='utf-8') as f: f.write(text2)

print('Fixed repositories')
