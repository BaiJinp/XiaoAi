import ReactMarkdown from 'react-markdown';

interface MarkdownArtifactProps {
  content: string;
}

export function MarkdownArtifact({ content }: MarkdownArtifactProps) {
  return <ReactMarkdown>{content}</ReactMarkdown>;
}
