import { useState } from "react";

// The last answer a list's query gave, with the list state it answered.
// While the next filters load, what is said about the list stays the last
// answer's, words and figures together, rather than the next filters' words
// with the last figures. `currentData` is RTK Query's answer for the
// current arguments, which is missing while they load.
const useLastAnswer = (currentData, list) => {
  const [last, setLast] = useState({ data: currentData, list });
  if (currentData && currentData !== last.data) {
    const answered = { data: currentData, list };
    setLast(answered);
    return answered;
  }
  return last;
};

export default useLastAnswer;
