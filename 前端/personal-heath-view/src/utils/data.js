export function timeAgo(dateString) {
  const now = new Date();
  const date = new Date(dateString);
  const secondsPast = (now.getTime() - date.getTime()) / 1000;
  if (secondsPast < 60) {
    return `${Math.floor(secondsPast)} `;
  } else if (secondsPast < 3600) {
    return `${Math.floor(secondsPast / 60)} `;
  } else if (secondsPast <= 86400) {
    return `${Math.floor(secondsPast / 3600)} `;
  } else {
    const daysPast = Math.floor(secondsPast / 86400);
    if (daysPast === 1) {
      return "1 ";
    } else {
      return `${daysPast} `;
    }
  }
}
