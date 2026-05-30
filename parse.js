const fs = require('fs');
const path = require('path');
const dir = 'c:/Users/Madhav/Desktop/Springboot project/AI/AI/src/main/java/com/JanSahayak/AI/model';
const files = fs.readdirSync(dir).filter(f => f.endsWith('.java'));
for (const file of files) {
  const content = fs.readFileSync(path.join(dir, file), 'utf8');
  let tableName = file.replace('.java', '');
  const tableMatch = content.match(/@Table\(name\s*=\s*"([^"]+)"\)/);
  if (tableMatch) tableName = tableMatch[1];
  
  const userRefs = [];
  const lines = content.split('\n');
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes('private User ') || lines[i].includes('private List<User> ')) {
      let colName = 'user_id'; // default fallback
      if (i > 0 && lines[i-1].includes('@JoinColumn')) {
         const colMatch = lines[i-1].match(/name\s*=\s*"([^"]+)"/);
         if (colMatch) colName = colMatch[1];
      } else if (i > 1 && lines[i-2].includes('@JoinColumn')) {
         const colMatch = lines[i-2].match(/name\s*=\s*"([^"]+)"/);
         if (colMatch) colName = colMatch[1];
      }
      userRefs.push({ field: lines[i].trim(), column: colName });
    }
  }
  if (userRefs.length > 0) {
    console.log(tableName, userRefs);
  }
}
