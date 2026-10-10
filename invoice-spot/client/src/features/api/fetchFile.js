// A file, such as a PDF or a CSV export, needs the sign-in token, so it comes
// back as a blob and is handed over as an object URL, which the caller revokes.
export const fetchFile = async (baseQuery, url) => {
  const result = await baseQuery({
    url,
    responseHandler: (response) =>
      response.ok ? response.blob() : response.json(),
  });
  if (result.error) return { error: result.error };
  return { data: URL.createObjectURL(result.data) };
};
